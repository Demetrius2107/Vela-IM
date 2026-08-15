package com.vela.im.service.knowledge.domain.service.vector;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vela.im.service.knowledge.domain.entity.DocVectorEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocVectorMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MySQL 向量存储（默认实现）：向量以 JSON 数组存 vela_doc_vector.vector 字段。
 * 检索方式：全量取回 + 余弦相似度排序（适合中小规模知识库）；
 * 数据量增大后替换 {@link VectorStore} Bean 为 Milvus/ES 即可。
 */
@Component
public class MySqlVectorStore implements VectorStore {

    private final DocVectorMapper vectorMapper;

    public MySqlVectorStore(DocVectorMapper vectorMapper) {
        this.vectorMapper = vectorMapper;
    }

    @Override
    public void index(Integer appId, Long docId, List<String> chunks, List<float[]> vectors) {
        removeByDoc(docId);
        if (chunks == null) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < chunks.size(); i++) {
            DocVectorEntity e = new DocVectorEntity();
            e.setAppId(appId);
            e.setDocId(docId);
            e.setChunkNo(i);
            e.setContent(chunks.get(i));
            e.setVector(JSON.toJSONString(vectors.get(i)));
            e.setCreateTime(now);
            vectorMapper.insert(e);
        }
    }

    @Override
    public void removeByDoc(Long docId) {
        QueryWrapper<DocVectorEntity> q = new QueryWrapper<>();
        q.eq("doc_id", docId);
        vectorMapper.delete(q);
    }

    @Override
    public List<VectorHit> search(Integer appId, float[] queryVector, int topN) {
        QueryWrapper<DocVectorEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId);
        List<DocVectorEntity> all = vectorMapper.selectList(q);
        List<VectorHit> hits = new ArrayList<>();
        for (DocVectorEntity e : all) {
            float[] vec = JSON.parseObject(e.getVector(), float[].class);
            float score = cosine(vec, queryVector);
            hits.add(new VectorHit(e.getDocId(), e.getChunkNo(), e.getContent(), score));
        }
        hits.sort(Comparator.comparingDouble(VectorHit::getScore).reversed());
        if (hits.size() > topN) hits = new ArrayList<>(hits.subList(0, topN));
        return hits;
    }

    private float cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        float dot = 0f, na = 0f, nb = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0f || nb == 0f) return 0f;
        return dot / (float) (Math.sqrt(na) * Math.sqrt(nb));
    }
}
