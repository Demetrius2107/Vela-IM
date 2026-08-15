package com.vela.im.service.knowledge.domain.service.vector;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认 RAG 回答器：不生成，返回 null 触发降级为「候选片段列表」。
 * 生产接入 LLM 时，提供新的 {@link RagAnswerer} Bean 覆盖即可。
 */
@Component
public class NoopRagAnswerer implements RagAnswerer {

    @Override
    public String answer(String question, List<VectorHit> hits) {
        return null;
    }
}
