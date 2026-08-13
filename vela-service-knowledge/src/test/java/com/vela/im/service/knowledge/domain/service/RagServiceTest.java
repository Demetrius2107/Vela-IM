package com.vela.im.service.knowledge.domain.service;

import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.domain.service.vector.EmbeddingProvider;
import com.vela.im.service.knowledge.domain.service.vector.RagAnswerer;
import com.vela.im.service.knowledge.domain.service.vector.VectorHit;
import com.vela.im.service.knowledge.domain.service.vector.VectorStore;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock VectorStore vectorStore;
    @Mock EmbeddingProvider embeddingProvider;
    @Mock RagAnswerer ragAnswerer;
    @Mock DocumentMapper documentMapper;

    @InjectMocks RagService service;

    @Test
    void chunk_splitsByParagraph() {
        String text = "第一段内容。\n\n第二段内容，比较长的一段话，用于验证分块逻辑是否正确工作。";
        List<String> chunks = RagService.chunk(text);
        assertEquals(2, chunks.size());
    }

    @Test
    void chunk_splitsLongParagraph_bySentence() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("这是第").append(i).append("个句子，内容用于测试分块。");
        }
        List<String> chunks = RagService.chunk(sb.toString());
        assertFalse(chunks.isEmpty());
        for (String c : chunks) {
            assertTrue(c.length() <= 560); // CHUNK_MAX + 重叠余量
        }
        assertTrue(chunks.size() > 1);
    }

    @Test
    void ask_returnsCandidates_whenHitsFound() {
        when(vectorStore.search(anyInt(), any(), anyInt())).thenReturn(Collections.singletonList(
                new VectorHit(1L, 0, "开通Vela账号的详细流程。", 0.9f)));
        DocumentEntity doc = new DocumentEntity();
        doc.setId(1L);
        doc.setTitle("如何开通Vela账号");
        doc.setSummary("摘要");
        doc.setStatus(DocumentService.STATUS_PUBLISHED);
        when(documentMapper.selectById(1L)).thenReturn(doc);
        when(ragAnswerer.answer(any(), any())).thenReturn(null); // 降级模式

        Result<Map<String, Object>> r = service.ask(100, "如何开通账号", 3);

        assertTrue(r.isOk());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) r.getData().get("candidates");
        assertEquals(1, candidates.size());
        assertEquals(1L, candidates.get(0).get("docId"));
        assertEquals(false, r.getData().get("generated"));
    }

    @Test
    void ask_filtersNonPublishedDocs() {
        when(vectorStore.search(anyInt(), any(), anyInt())).thenReturn(Collections.singletonList(
                new VectorHit(2L, 0, "草稿内容。", 0.8f)));
        DocumentEntity doc = new DocumentEntity();
        doc.setId(2L);
        doc.setTitle("草稿");
        doc.setStatus(DocumentService.STATUS_DRAFT);
        when(documentMapper.selectById(2L)).thenReturn(doc);

        Result<Map<String, Object>> r = service.ask(100, "草稿", 3);

        assertTrue(r.isOk());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) r.getData().get("candidates");
        assertTrue(candidates.isEmpty());
    }

    @Test
    void ask_noHits_returnsFallback() {
        when(vectorStore.search(anyInt(), any(), anyInt())).thenReturn(new ArrayList<>());

        Result<Map<String, Object>> r = service.ask(100, "没有结果的问题", 3);

        assertTrue(r.isOk());
        assertTrue(((String) r.getData().get("answer")).contains("没有找到"));
    }

    @Test
    void reindex_embedsAndStoresChunks() {
        DocumentEntity doc = new DocumentEntity();
        doc.setId(1L);
        doc.setAppId(100);
        doc.setContent("第一段。\n\n第二段。");
        when(embeddingProvider.embed(any())).thenReturn(new float[]{0.1f, 0.2f});

        service.reindex(doc);

        org.mockito.Mockito.verify(vectorStore).index(anyInt(), any(), any(), any());
    }
}
