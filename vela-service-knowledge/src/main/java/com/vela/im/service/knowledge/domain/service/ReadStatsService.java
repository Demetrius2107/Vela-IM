package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vela.im.service.knowledge.domain.entity.DocReadEntity;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocReadMapper;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.BusinessErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 阅读统计服务。
 * 业务规则：
 * 1. 同一用户同一文档每天只计一次阅读（当日去重）；
 * 2. 文档冗余 readCount 与阅读记录严格一致（新增记录才 +1）；
 * 3. 阅读数/收藏数为 FAQ 热榜（任务 #14）的基础数据。
 */
@Service
public class ReadStatsService {

    private final DocReadMapper readMapper;
    private final DocumentMapper documentMapper;

    public ReadStatsService(DocReadMapper readMapper, DocumentMapper documentMapper) {
        this.readMapper = readMapper;
        this.documentMapper = documentMapper;
    }

    /**
     * 记录一次阅读：当日已有记录则幂等返回；否则插入并 +1 阅读数。
     */
    @Transactional
    public Result<Void> record(Integer appId, String userId, Long docId) {
        DocumentEntity doc = documentMapper.selectById(docId);
        if (doc == null || (doc.getIsDeleted() != null && doc.getIsDeleted() == 1)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        }
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        QueryWrapper<DocReadEntity> q = new QueryWrapper<>();
        q.eq("doc_id", docId).eq("user_id", userId).eq("read_date", today);
        if (readMapper.selectCount(q) > 0) {
            return Result.ok(); // 当日已计
        }
        DocReadEntity r = new DocReadEntity();
        r.setAppId(appId);
        r.setDocId(docId);
        r.setUserId(userId);
        r.setReadDate(today);
        r.setCreateTime(System.currentTimeMillis());
        readMapper.insert(r);
        doc.setReadCount((doc.getReadCount() == null ? 0 : doc.getReadCount()) + 1);
        documentMapper.updateById(doc);
        return Result.ok();
    }

    public Result<Map<String, Object>> stats(Long docId) {
        DocumentEntity doc = documentMapper.selectById(docId);
        if (doc == null) return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        Map<String, Object> r = new HashMap<>();
        r.put("docId", doc.getId());
        r.put("readCount", doc.getReadCount());
        r.put("favoriteCount", doc.getFavoriteCount());
        return Result.ok(r);
    }
}
