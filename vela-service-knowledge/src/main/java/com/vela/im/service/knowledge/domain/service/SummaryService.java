package com.vela.im.service.knowledge.domain.service;

import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.domain.service.summary.Summarizer;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.BusinessErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 自动摘要服务：
 * 1. 文档保存时若摘要为空，自动抽取关键句作为摘要（手动填写优先）；
 * 2. 提供显式生成接口，可覆盖已有摘要。
 */
@Service
public class SummaryService {

    private final DocumentMapper documentMapper;
    private final Summarizer summarizer;

    public SummaryService(DocumentMapper documentMapper, Summarizer summarizer) {
        this.documentMapper = documentMapper;
        this.summarizer = summarizer;
    }

    /**
     * 生成并保存摘要。
     *
     * @param force 为 true 时覆盖已有摘要；false 时仅在摘要为空时生成
     */
    public Result<String> generate(Long docId, boolean force) {
        DocumentEntity doc = documentMapper.selectById(docId);
        if (doc == null) return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        if (!force && StringUtils.hasText(doc.getSummary())) {
            return Result.ok(doc.getSummary()); // 手动摘要优先，不覆盖
        }
        String summary = summarizer.summarize(doc.getContent());
        doc.setSummary(summary);
        doc.setUpdateTime(System.currentTimeMillis());
        documentMapper.updateById(doc);
        return Result.ok(summary);
    }

    /** 保存文档时兜底：摘要为空则自动生成（不覆盖手填） */
    public void ensureSummary(DocumentEntity doc) {
        if (doc == null || doc.getId() == null) return;
        if (StringUtils.hasText(doc.getSummary())) return;
        String summary = summarizer.summarize(doc.getContent());
        if (StringUtils.hasText(summary)) {
            doc.setSummary(summary);
            documentMapper.updateById(doc);
        }
    }
}
