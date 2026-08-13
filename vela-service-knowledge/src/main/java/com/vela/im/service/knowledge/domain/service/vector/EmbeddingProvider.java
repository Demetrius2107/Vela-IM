package com.vela.im.service.knowledge.domain.service.vector;

/**
 * 文本向量化抽象（可插拔）。
 * 默认实现 {@link LocalHashEmbedding}；生产可替换为 OpenAI/BGE/M3E 等 Embedding 服务。
 */
public interface EmbeddingProvider {

    /** 向量维度 */
    int dimension();

    /** 将文本转为向量 */
    float[] embed(String text);
}
