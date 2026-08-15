package com.vela.im.service.knowledge.domain.service.vector;

import java.util.List;

/**
 * RAG 生成式回答抽象（可插拔 LLM）。
 * 默认实现 {@link NoopRagAnswerer} 返回 null，触发降级——接口只返回候选片段列表；
 * 接入真实 LLM 时实现本接口，拼接候选片段生成带引用的答案。
 */
public interface RagAnswerer {

    /**
     * 基于候选片段生成回答。
     *
     * @return 生成的回答文本；返回 null 表示不生成，调用方降级为候选列表
     */
    String answer(String question, List<VectorHit> hits);
}
