package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.domain.service.vector.EmbeddingProvider;
import com.vela.im.service.knowledge.domain.service.vector.RagAnswerer;
import com.vela.im.service.knowledge.domain.service.vector.VectorHit;
import com.vela.im.service.knowledge.domain.service.vector.VectorStore;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.BusinessErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索增强问答服务。
 * 流程：正文分块 → Embedding 向量化 → 向量库检索 Top-N 候选片段 →
 * 聚合片段生成回答（RagAnswerer 可插拔；默认不生成，降级返回候选列表）。
 */
@Service
public class RagService {

    /** 单块最大字符数 */
    private static final int CHUNK_MAX = 500;
    /** 块重叠字符数（保证跨块语义不丢） */
    private static final int CHUNK_OVERLAP = 60;

    private final VectorStore vectorStore;
    private final EmbeddingProvider embeddingProvider;
    private final RagAnswerer ragAnswerer;
    private final DocumentMapper documentMapper;

    public RagService(VectorStore vectorStore, EmbeddingProvider embeddingProvider,
                      RagAnswerer ragAnswerer, DocumentMapper documentMapper) {
        this.vectorStore = vectorStore;
        this.embeddingProvider = embeddingProvider;
        this.ragAnswerer = ragAnswerer;
        this.documentMapper = documentMapper;
    }

    /**
     * RAG 问答：向量检索 + 可选生成。
     */
    public Result<Map<String, Object>> ask(Integer appId, String question, int limit) {
        if (question == null || question.trim().isEmpty()) {
            return Result.fail(BusinessErrorCode.BAD_REQUEST);
        }
        int n = Math.min(Math.max(limit <= 0 ? 3 : limit, 1), 10);
        float[] qv = embeddingProvider.embed(question);
        List<VectorHit> hits = vectorStore.search(appId, qv, n * 3);

        // 按文档聚合，取每篇文档得分最高的片段
        Map<Long, VectorHit> bestByDoc = new LinkedHashMap<>();
        for (VectorHit h : hits) {
            if (!bestByDoc.containsKey(h.getDocId()) || h.getScore() > bestByDoc.get(h.getDocId()).getScore()) {
                bestByDoc.put(h.getDocId(), h);
            }
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map.Entry<Long, VectorHit> en : bestByDoc.entrySet()) {
            DocumentEntity doc = documentMapper.selectById(en.getKey());
            if (doc == null || (doc.getIsDeleted() != null && doc.getIsDeleted() == 1)) continue;
            if (doc.getStatus() == null || doc.getStatus() != DocumentService.STATUS_PUBLISHED) continue;
            Map<String, Object> c = new HashMap<>();
            c.put("docId", doc.getId());
            c.put("title", doc.getTitle());
            c.put("summary", doc.getSummary());
            c.put("chunk", en.getValue().getContent());
            c.put("score", en.getValue().getScore());
            candidates.add(c);
            if (candidates.size() >= n) break;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("question", question);
        result.put("candidates", candidates);
        if (candidates.isEmpty()) {
            result.put("answer", "知识库中没有找到与「" + question + "」相关的已发布内容。");
            result.put("generated", false);
            return Result.ok(result);
        }
        // 生成式回答：RagAnswerer 返回 null 时降级为候选列表
        String answer = ragAnswerer.answer(question, new ArrayList<>(bestByDoc.values()));
        if (answer != null && !answer.trim().isEmpty()) {
            result.put("answer", answer);
            result.put("generated", true);
        } else {
            result.put("answer", null);
            result.put("generated", false);
        }
        return Result.ok(result);
    }

    /**
     * 重建单文档向量索引（文档创建/更新/回滚后调用，或运维手动触发）。
     */
    public void reindex(DocumentEntity doc) {
        if (doc == null || doc.getId() == null) return;
        String content = doc.getContent();
        if (content == null || content.trim().isEmpty()) {
            vectorStore.removeByDoc(doc.getId());
            return;
        }
        List<String> chunks = chunk(content);
        List<float[]> vectors = new ArrayList<>();
        for (String c : chunks) vectors.add(embeddingProvider.embed(c));
        vectorStore.index(doc.getAppId(), doc.getId(), chunks, vectors);
    }

    /** 按文档ID重建索引（运维接口） */
    public Result<Void> reindexById(Long docId) {
        DocumentEntity doc = documentMapper.selectById(docId);
        if (doc == null) return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        reindex(doc);
        return Result.ok();
    }

    /**
     * 文本分块：按段落（空行）切分，超长段落再按句子（。！？）切分，
     * 相邻块保留 CHUNK_OVERLAP 字符重叠。
     */
    static List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return chunks;
        String[] paragraphs = text.split("\\n\\s*\\n");
        for (String p : paragraphs) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (p.length() <= CHUNK_MAX) {
                chunks.add(p);
                continue;
            }
            // 按句子切分再合并
            String[] sentences = p.split("(?<=[。！？!?；;])");
            StringBuilder buf = new StringBuilder();
            for (String s : sentences) {
                if (s.trim().isEmpty()) continue;
                if (buf.length() + s.length() > CHUNK_MAX && buf.length() > 0) {
                    chunks.add(buf.toString());
                    buf = new StringBuilder(overlapTail(chunks.get(chunks.size() - 1)));
                }
                buf.append(s);
            }
            if (buf.length() > 0) chunks.add(buf.toString());
        }
        return chunks;
    }

    private static String overlapTail(String prev) {
        if (prev == null || prev.length() <= CHUNK_OVERLAP) return prev == null ? "" : prev;
        return prev.substring(prev.length() - CHUNK_OVERLAP);
    }

    /** 清理文档向量（永久删除时） */
    public void removeIndex(Long docId) {
        vectorStore.removeByDoc(docId);
    }
}
