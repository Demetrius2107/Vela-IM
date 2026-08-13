package com.vela.im.service.knowledge.domain.service.summary;

/**
 * 摘要生成抽象（可插拔）。
 * 默认实现 {@link ExtractiveSummarizer}（本地抽取式，零依赖）；
 * 生产可替换为 LLM 摘要服务。
 */
public interface Summarizer {

    /**
     * 生成摘要。
     *
     * @return 摘要文本；返回 null 表示不生成（如内容过短无需摘要）
     */
    String summarize(String content);
}
