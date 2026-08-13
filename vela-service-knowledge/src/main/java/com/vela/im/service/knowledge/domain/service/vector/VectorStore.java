package com.vela.im.service.knowledge.domain.service.vector;

import java.util.List;

/**
 * 向量存储抽象（可插拔后端）。
 * 默认实现 {@link MySqlVectorStore}；数据量大时可替换为 Milvus/Elasticsearch 等。
 */
public interface VectorStore {

    /**
     * 索引文档分块：先清理旧索引再写入。
     */
    void index(Integer appId, Long docId, List<String> chunks, List<float[]> vectors);

    /** 删除某文档的全部向量 */
    void removeByDoc(Long docId);

    /** 按查询向量取 Top-N 命中（余弦相似度） */
    List<VectorHit> search(Integer appId, float[] queryVector, int topN);
}
