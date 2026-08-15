package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vela.im.service.knowledge.domain.entity.DocFavoriteEntity;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocFavoriteMapper;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.BusinessErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档收藏服务：收藏/取消（幂等）/收藏列表。
 */
@Service
public class FavoriteService {

    private final DocFavoriteMapper favoriteMapper;
    private final DocumentMapper documentMapper;

    public FavoriteService(DocFavoriteMapper favoriteMapper, DocumentMapper documentMapper) {
        this.favoriteMapper = favoriteMapper;
        this.documentMapper = documentMapper;
    }

    @Transactional
    public Result<Void> add(Integer appId, String userId, Long docId) {
        DocumentEntity doc = documentMapper.selectById(docId);
        if (doc == null || (doc.getIsDeleted() != null && doc.getIsDeleted() == 1)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        }
        QueryWrapper<DocFavoriteEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("user_id", userId).eq("doc_id", docId);
        if (favoriteMapper.selectCount(q) > 0) {
            return Result.ok(); // 幂等：已收藏视为成功
        }
        DocFavoriteEntity f = new DocFavoriteEntity();
        f.setAppId(appId);
        f.setUserId(userId);
        f.setDocId(docId);
        f.setCreateTime(System.currentTimeMillis());
        favoriteMapper.insert(f);
        doc.setFavoriteCount((doc.getFavoriteCount() == null ? 0 : doc.getFavoriteCount()) + 1);
        documentMapper.updateById(doc);
        return Result.ok();
    }

    @Transactional
    public Result<Void> remove(Integer appId, String userId, Long docId) {
        QueryWrapper<DocFavoriteEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("user_id", userId).eq("doc_id", docId);
        DocFavoriteEntity f = favoriteMapper.selectOne(q);
        if (f == null) {
            return Result.ok(); // 幂等：未收藏视为成功
        }
        favoriteMapper.deleteById(f.getId());
        DocumentEntity doc = documentMapper.selectById(docId);
        if (doc != null && doc.getFavoriteCount() != null && doc.getFavoriteCount() > 0) {
            doc.setFavoriteCount(doc.getFavoriteCount() - 1);
            documentMapper.updateById(doc);
        }
        return Result.ok();
    }

    public Result<Map<String, Object>> list(Integer appId, String userId, int page, int size) {
        QueryWrapper<DocFavoriteEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("user_id", userId).orderByDesc("create_time");
        IPage<DocFavoriteEntity> p = favoriteMapper.selectPage(new Page<>(page + 1, size), q);
        List<Map<String, Object>> items = new ArrayList<>();
        for (DocFavoriteEntity f : p.getRecords()) {
            DocumentEntity doc = documentMapper.selectById(f.getDocId());
            if (doc == null || (doc.getIsDeleted() != null && doc.getIsDeleted() == 1)) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("favoriteId", f.getId());
            item.put("favoriteTime", f.getCreateTime());
            item.put("doc", doc);
            items.add(item);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("list", items);
        r.put("total", p.getTotal());
        return Result.ok(r);
    }
}
