package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vela.im.service.knowledge.domain.entity.DocVersionEntity;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocVersionMapper;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.BusinessErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档版本历史服务。
 * 业务规则：
 * 1. 内容 diff 检测——标题/正文/摘要均未变化时不产生新版本，避免空版本堆积；
 * 2. 版本号单调递增，首个版本为 1；
 * 3. 容量保护——每文档最多保留 MAX_VERSIONS 个版本，超出清理最旧；
 * 4. 回滚一致性——回滚 = 当前内容存为新版本 + 文档内容还原到目标版本，历史永不丢失。
 */
@Service
public class VersionService {

    /** 每文档最大保留版本数 */
    public static final int MAX_VERSIONS = 50;

    private final DocVersionMapper versionMapper;
    private final DocumentMapper documentMapper;
    private final PermissionService permissionService;
    private final RagService ragService;

    public VersionService(DocVersionMapper versionMapper, DocumentMapper documentMapper,
                          PermissionService permissionService, RagService ragService) {
        this.versionMapper = versionMapper;
        this.documentMapper = documentMapper;
        this.permissionService = permissionService;
        this.ragService = ragService;
    }

    /**
     * 保存快照：文档每次保存新版本时调用。
     * 内容 diff 检测：与最新版本比对，标题/正文/摘要均相同则跳过。
     */
    @Transactional
    public Result<DocVersionEntity> saveSnapshot(DocumentEntity doc, String editorId) {
        if (doc == null || doc.getId() == null) return Result.fail(BusinessErrorCode.BAD_REQUEST);
        DocVersionEntity latest = latest(doc.getId());
        if (latest != null && !changed(latest, doc)) {
            return Result.ok(latest); // 内容无变化，不产生新版本
        }
        DocVersionEntity v = new DocVersionEntity();
        v.setAppId(doc.getAppId());
        v.setDocId(doc.getId());
        v.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        v.setTitle(doc.getTitle());
        v.setContent(doc.getContent());
        v.setSummary(doc.getSummary());
        v.setEditorId(editorId);
        v.setCreateTime(System.currentTimeMillis());
        versionMapper.insert(v);
        trim(doc.getId());
        return Result.ok(v);
    }

    /** 内容是否变化：任一字段不同即视为变化 */
    private boolean changed(DocVersionEntity v, DocumentEntity doc) {
        return !eq(v.getTitle(), doc.getTitle())
                || !eq(v.getContent(), doc.getContent())
                || !eq(v.getSummary(), doc.getSummary());
    }

    private boolean eq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    private DocVersionEntity latest(Long docId) {
        QueryWrapper<DocVersionEntity> q = new QueryWrapper<>();
        q.eq("doc_id", docId).orderByDesc("version_no").last("LIMIT 1");
        return versionMapper.selectOne(q);
    }

    /** 容量保护：超出 MAX_VERSIONS 删除最旧版本 */
    private void trim(Long docId) {
        QueryWrapper<DocVersionEntity> q = new QueryWrapper<>();
        q.eq("doc_id", docId);
        Integer count = versionMapper.selectCount(q);
        if (count == null || count <= MAX_VERSIONS) return;
        long excess = count - MAX_VERSIONS;
        QueryWrapper<DocVersionEntity> del = new QueryWrapper<>();
        del.eq("doc_id", docId).orderByAsc("version_no").last("LIMIT " + excess);
        List<DocVersionEntity> old = versionMapper.selectList(del);
        for (DocVersionEntity o : old) versionMapper.deleteById(o.getId());
    }

    public Result<Map<String, Object>> list(Long docId, int page, int size) {
        QueryWrapper<DocVersionEntity> q = new QueryWrapper<>();
        q.eq("doc_id", docId).orderByDesc("version_no");
        IPage<DocVersionEntity> p = versionMapper.selectPage(new Page<>(page + 1, size), q);
        Map<String, Object> r = new HashMap<>();
        r.put("list", p.getRecords());
        r.put("total", p.getTotal());
        return Result.ok(r);
    }

    /**
     * 回滚：将文档内容还原到目标版本。
     * 一致性保证：先以「当前内容」写入一个新版本（保留现场），再覆盖文档为目标版本，
     * 使回滚本身也可追溯、可撤销。
     */
    @Transactional
    public Result<Void> rollback(Integer appId, String userId, Long docId, Integer versionNo) {
        DocumentEntity doc = documentMapper.selectById(docId);
        if (doc == null || (doc.getIsDeleted() != null && doc.getIsDeleted() == 1)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (!permissionService.canWrite(appId, userId, doc)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        DocVersionEntity target = findByNo(docId, versionNo);
        if (target == null) return Result.fail(BusinessErrorCode.VERSION_NOT_FOUND);

        // 1. 把回滚前的当前状态存为新版本（版本号会继续递增）
        saveSnapshot(doc, userId);
        // 2. 文档内容还原到目标版本
        doc.setTitle(target.getTitle());
        doc.setContent(target.getContent());
        doc.setSummary(target.getSummary());
        doc.setUpdateTime(System.currentTimeMillis());
        documentMapper.updateById(doc);
        // 3. 将还原结果也落一个版本，保证历史连续
        saveSnapshot(doc, userId);
        // 4. 内容已变，重建向量索引
        ragService.reindex(doc);
        return Result.ok();
    }

    private DocVersionEntity findByNo(Long docId, Integer versionNo) {
        QueryWrapper<DocVersionEntity> q = new QueryWrapper<>();
        q.eq("doc_id", docId).eq("version_no", versionNo);
        return versionMapper.selectOne(q);
    }

    /** 文档永久删除时清理版本 */
    public void deleteByDoc(Long docId) {
        QueryWrapper<DocVersionEntity> q = new QueryWrapper<>();
        q.eq("doc_id", docId);
        versionMapper.delete(q);
    }
}
