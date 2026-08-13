package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vela.im.service.knowledge.domain.entity.DocFavoriteEntity;
import com.vela.im.service.knowledge.domain.entity.DocPermissionEntity;
import com.vela.im.service.knowledge.domain.entity.DocReadEntity;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocFavoriteMapper;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocPermissionMapper;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocReadMapper;
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
 * 知识库文档服务：CRUD + 全文检索 + 预览 + 回收站。
 */
@Service
public class DocumentService {

    /** 文档状态常量 */
    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PENDING = 1;
    public static final int STATUS_PUBLISHED = 2;
    public static final int STATUS_REJECTED = 3;

    private final DocumentMapper mapper;
    private final PermissionService permissionService;
    private final VersionService versionService;
    private final RagService ragService;
    private final SummaryService summaryService;
    private final DocFavoriteMapper favoriteMapper;
    private final DocPermissionMapper permissionMapper;
    private final DocReadMapper readMapper;

    public DocumentService(DocumentMapper mapper, PermissionService permissionService,
                           VersionService versionService, RagService ragService,
                           SummaryService summaryService, DocFavoriteMapper favoriteMapper,
                           DocPermissionMapper permissionMapper, DocReadMapper readMapper) {
        this.mapper = mapper;
        this.permissionService = permissionService;
        this.versionService = versionService;
        this.ragService = ragService;
        this.summaryService = summaryService;
        this.favoriteMapper = favoriteMapper;
        this.permissionMapper = permissionMapper;
        this.readMapper = readMapper;
    }

    public Result<DocumentEntity> create(DocumentEntity entity) {
        if (entity.getAppId() == null || !StringUtils.hasText(entity.getTitle())) {
            return Result.fail(BusinessErrorCode.BAD_REQUEST);
        }
        entity.setStatus(entity.getStatus() == null ? STATUS_DRAFT : entity.getStatus());
        entity.setIsDeleted(0);
        entity.setReadCount(0L);
        entity.setFavoriteCount(0L);
        entity.setCreateTime(System.currentTimeMillis());
        entity.setUpdateTime(entity.getCreateTime());
        mapper.insert(entity);
        summaryService.ensureSummary(entity);
        versionService.saveSnapshot(entity, entity.getCreatorId());
        ragService.reindex(entity);
        return Result.ok(entity);
    }

    public Result<Map<String, Object>> list(Integer appId, String keyword, Long categoryId, Integer status, int page, int size) {
        QueryWrapper<DocumentEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("is_deleted", 0);
        if (categoryId != null) q.eq("category_id", categoryId);
        if (status != null) q.eq("status", status);
        if (keyword != null && !keyword.isEmpty())
            q.and(w -> w.like("title", keyword).or().like("content", keyword)
                    .or().like("summary", keyword).or().like("tags", keyword));
        q.orderByDesc("update_time");
        IPage<DocumentEntity> p = mapper.selectPage(new Page<>(page + 1, size), q);
        return pageResult(p);
    }

    /**
     * 全文检索：多字段 LIKE + 分类过滤 + 状态过滤（默认只出已发布）+ 字段加权评分排序 + 关键词高亮。
     * 权重规则：标题=5 > 摘要=3 > 标签=2 > 正文=1（标题命中说明意图最相关）。
     */
    public Result<Map<String, Object>> search(Integer appId, String keyword, Long categoryId,
                                              Integer status, int page, int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.fail(BusinessErrorCode.BAD_REQUEST);
        }
        String kw = keyword.trim();
        QueryWrapper<DocumentEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("is_deleted", 0);
        q.eq("status", status == null ? STATUS_PUBLISHED : status);
        if (categoryId != null) q.eq("category_id", categoryId);
        q.and(w -> w.like("title", kw).or().like("content", kw)
                .or().like("summary", kw).or().like("tags", kw));
        // 相关性评分：标题命中=5，摘要=3，标签=2，正文=1；命中多个字段分数累加
        q.last("ORDER BY (" +
                "IF(LOCATE('" + escapeLike(kw) + "', title) > 0, 5, 0)" +
                "+ IF(LOCATE('" + escapeLike(kw) + "', summary) > 0, 3, 0)" +
                "+ IF(LOCATE('" + escapeLike(kw) + "', tags) > 0, 2, 0)" +
                "+ IF(LOCATE('" + escapeLike(kw) + "', content) > 0, 1, 0)" +
                ") DESC, update_time DESC");
        IPage<DocumentEntity> p = mapper.selectPage(new Page<>(page + 1, size), q);
        for (DocumentEntity d : p.getRecords()) {
            d.setHighlight(highlight(d, kw));
        }
        return pageResult(p);
    }

    /** 转义 LIKE 特殊字符，防止检索词注入 SQL */
    private String escapeLike(String kw) {
        return kw.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** 生成高亮片段：优先命中标题，否则取正文命中上下文 */
    private String highlight(DocumentEntity d, String kw) {
        String title = d.getTitle() == null ? "" : d.getTitle();
        int ti = title.toLowerCase().indexOf(kw.toLowerCase());
        if (ti >= 0) {
            return "<em>" + title.substring(ti, Math.min(title.length(), ti + kw.length())) + "</em>"
                    + (title.length() > ti + kw.length() ? title.substring(ti + kw.length(), Math.min(title.length(), ti + kw.length() + 20)) : "");
        }
        String content = d.getContent() == null ? "" : d.getContent();
        int ci = content.toLowerCase().indexOf(kw.toLowerCase());
        if (ci >= 0) {
            int start = Math.max(0, ci - 30);
            int end = Math.min(content.length(), ci + kw.length() + 30);
            return (start > 0 ? "…" : "") + content.substring(start, end) + (end < content.length() ? "…" : "");
        }
        return "";
    }

    public Result<DocumentEntity> get(Integer appId, String userId, Long id) {
        DocumentEntity e = mapper.selectById(id);
        if (e == null || (e.getIsDeleted() != null && e.getIsDeleted() == 1)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (!permissionService.canRead(appId, userId, e)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        return Result.ok(e);
    }

    /**
     * 消息卡片预览：只返回脱敏卡片信息（不含正文全文），供聊天链接预览渲染。
     * 无权限用户返回「无权访问」占位，不泄露标题之外的敏感信息。
     */
    public Result<Map<String, Object>> preview(Integer appId, String userId, Long id) {
        DocumentEntity e = mapper.selectById(id);
        if (e == null || (e.getIsDeleted() != null && e.getIsDeleted() == 1)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        }
        Map<String, Object> card = new HashMap<>();
        card.put("id", e.getId());
        if (!permissionService.canRead(appId, userId, e)) {
            card.put("accessDenied", true);
            card.put("title", "无权访问");
            return Result.ok(card);
        }
        card.put("accessDenied", false);
        card.put("title", e.getTitle());
        card.put("summary", e.getSummary());
        card.put("creatorId", e.getCreatorId());
        card.put("categoryId", e.getCategoryId());
        card.put("tags", e.getTags());
        card.put("status", e.getStatus());
        card.put("updateTime", e.getUpdateTime());
        card.put("readCount", e.getReadCount());
        card.put("favoriteCount", e.getFavoriteCount());
        return Result.ok(card);
    }

    /**
     * 聊天引用摘要：比 preview 更精简（标题+摘要+ID），供消息引用卡片使用。
     */
    public Result<Map<String, Object>> reference(Integer appId, String userId, Long id) {
        DocumentEntity e = mapper.selectById(id);
        if (e == null || (e.getIsDeleted() != null && e.getIsDeleted() == 1)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (!permissionService.canRead(appId, userId, e)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        Map<String, Object> ref = new HashMap<>();
        ref.put("id", e.getId());
        ref.put("title", e.getTitle());
        ref.put("summary", e.getSummary());
        ref.put("updateTime", e.getUpdateTime());
        return Result.ok(ref);
    }

    public Result<Void> update(Integer appId, String userId, DocumentEntity entity) {
        DocumentEntity e = mapper.selectById(entity.getId());
        if (e == null || (e.getIsDeleted() != null && e.getIsDeleted() == 1)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (!permissionService.canWrite(appId, userId, e)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        if (entity.getTitle() != null) e.setTitle(entity.getTitle());
        if (entity.getContent() != null) e.setContent(entity.getContent());
        if (entity.getSummary() != null) e.setSummary(entity.getSummary());
        if (entity.getTags() != null) e.setTags(entity.getTags());
        if (entity.getCategoryId() != null) e.setCategoryId(entity.getCategoryId());
        e.setUpdateTime(System.currentTimeMillis());
        mapper.updateById(e);
        summaryService.ensureSummary(e);
        versionService.saveSnapshot(e, userId);
        ragService.reindex(e);
        return Result.ok();
    }

    public Result<Void> delete(Integer appId, String userId, Long id) {
        DocumentEntity e = mapper.selectById(id);
        if (e == null) return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        if (!permissionService.canWrite(appId, userId, e)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        e.setIsDeleted(1);
        e.setUpdateTime(System.currentTimeMillis());
        mapper.updateById(e);
        return Result.ok();
    }

    // ==================== 回收站 ====================

    public Result<Map<String, Object>> recycleList(Integer appId, int page, int size) {
        QueryWrapper<DocumentEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("is_deleted", 1).orderByDesc("update_time");
        IPage<DocumentEntity> p = mapper.selectPage(new Page<>(page + 1, size), q);
        return pageResult(p);
    }

    public Result<Void> restore(Integer appId, String userId, Long id) {
        DocumentEntity e = mapper.selectById(id);
        if (e == null || e.getIsDeleted() == null || e.getIsDeleted() != 1) {
            return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (!permissionService.canWrite(appId, userId, e)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        e.setIsDeleted(0);
        e.setUpdateTime(System.currentTimeMillis());
        mapper.updateById(e);
        return Result.ok();
    }

    /**
     * 永久删除（限作者/管理员）：事务性级联清理版本/收藏/权限/阅读记录，防止孤儿数据。
     */
    @Transactional
    public Result<Void> purge(Integer appId, String userId, Long id) {
        DocumentEntity e = mapper.selectById(id);
        if (e == null) return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        if (!permissionService.canWrite(appId, userId, e)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        mapper.deleteById(id);
        versionService.deleteByDoc(id);
        ragService.removeIndex(id);
        QueryWrapper<DocFavoriteEntity> fq = new QueryWrapper<>();
        fq.eq("doc_id", id);
        favoriteMapper.delete(fq);
        QueryWrapper<DocPermissionEntity> pq = new QueryWrapper<>();
        pq.eq("doc_id", id);
        permissionMapper.delete(pq);
        QueryWrapper<DocReadEntity> rq = new QueryWrapper<>();
        rq.eq("doc_id", id);
        readMapper.delete(rq);
        return Result.ok();
    }

    private Result<Map<String, Object>> pageResult(IPage<DocumentEntity> p) {
        Map<String, Object> r = new HashMap<>();
        r.put("list", p.getRecords());
        r.put("total", p.getTotal());
        return Result.ok(r);
    }
}
