package com.vela.im.service.knowledge.domain.service;

import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotAskServiceTest {

    @Mock DocumentMapper documentMapper;

    @InjectMocks BotAskService service;

    private DocumentEntity doc(long id, String title, String content) {
        DocumentEntity d = new DocumentEntity();
        d.setId(id);
        d.setTitle(title);
        d.setContent(content);
        d.setSummary("summary" + id);
        d.setStatus(DocumentService.STATUS_PUBLISHED);
        return d;
    }

    @Test
    void ask_returnsRankedCandidates() {
        List<DocumentEntity> all = new ArrayList<>();
        all.add(doc(1L, "如何开通Vela账号", "开通Vela账号的详细流程，首先注册，然后验证。"));
        all.add(doc(2L, "Vela 群组管理", "群组的管理方法，包括创建群聊、设置管理员。"));
        when(documentMapper.selectList(any())).thenReturn(all);

        Result<Map<String, Object>> r = service.ask(100, "如何开通Vela账号", 3);

        assertTrue(r.isOk());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) r.getData().get("hits");
        assertFalse(hits.isEmpty());
        assertEquals(1L, hits.get(0).get("docId")); // 标题+正文双重命中排第一
        assertNotNull(r.getData().get("keywords"));
        assertTrue(((String) r.getData().get("answer")).contains("找到"));
    }

    @Test
    void ask_emptyQuestion_badRequest() {
        Result<Map<String, Object>> r = service.ask(100, "   ", 3);
        assertFalse(r.isOk());
    }

    @Test
    void ask_noMatch_returnsGuidance() {
        when(documentMapper.selectList(any())).thenReturn(new ArrayList<>());

        Result<Map<String, Object>> r = service.ask(100, "zzzz不存在的词yyy", 3);

        assertTrue(r.isOk());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) r.getData().get("hits");
        assertTrue(hits.isEmpty());
        assertTrue(((String) r.getData().get("answer")).contains("抱歉"));
    }

    @Test
    void ask_limitIsClamped() {
        when(documentMapper.selectList(any())).thenReturn(new ArrayList<>());
        Result<Map<String, Object>> r = service.ask(100, "测试问题", 999);
        assertTrue(r.isOk());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) r.getData().get("hits");
        assertEquals(0, hits.size()); // 不抛异常即可，limit 被钳制在 [1,10]
    }
}
