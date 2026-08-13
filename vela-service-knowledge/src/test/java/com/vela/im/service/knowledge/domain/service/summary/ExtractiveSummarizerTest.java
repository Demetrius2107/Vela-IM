package com.vela.im.service.knowledge.domain.service.summary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtractiveSummarizerTest {

    private final ExtractiveSummarizer summarizer = new ExtractiveSummarizer();

    @Test
    void summarize_empty_returnsEmpty() {
        assertEquals("", summarizer.summarize(null));
        assertEquals("", summarizer.summarize("  "));
    }

    @Test
    void summarize_shortText_returnsAsIs() {
        String text = "这是一篇短文。";
        assertEquals(text, summarizer.summarize(text));
    }

    @Test
    void summarize_longText_returnsKeySentences() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("这是第").append(i).append("句，内容用于摘要测试。");
        }
        String summary = summarizer.summarize(sb.toString());
        assertNotNull(summary);
        assertFalse(summary.isEmpty());
        assertTrue(summary.length() <= 250); // MAX_LENGTH + 分隔符余量
    }

    @Test
    void summarize_prefersInformativeSentences() {
        // 高频词句应被选中
        String content = "Vela知识库支持全文检索。Vela知识库支持版本回滚。Vela知识库支持权限管理。今天天气不错。中午吃了饭。";
        String summary = summarizer.summarize(content);
        assertTrue(summary.contains("Vela") || summary.contains("知识库"));
    }
}
