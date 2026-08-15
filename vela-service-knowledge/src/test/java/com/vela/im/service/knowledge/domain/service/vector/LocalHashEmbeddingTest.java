package com.vela.im.service.knowledge.domain.service.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalHashEmbeddingTest {

    private final LocalHashEmbedding embedding = new LocalHashEmbedding();

    @Test
    void embed_fixedDimension() {
        float[] v = embedding.embed("如何开通Vela账号");
        assertEquals(embedding.dimension(), v.length);
    }

    @Test
    void embed_emptyText_returnsZeroVector() {
        float[] v = embedding.embed("");
        for (float x : v) assertEquals(0f, x);
    }

    @Test
    void embed_similarTexts_havePositiveSimilarity() {
        float[] a = embedding.embed("如何开通Vela账号");
        float[] b = embedding.embed("开通Vela账号的流程");
        float[] c = embedding.embed("今天天气很好出去散步");

        assertTrue(cosine(a, b) > 0.1f);
        assertTrue(cosine(a, c) < cosine(a, b));
    }

    private float cosine(float[] a, float[] b) {
        float dot = 0f, na = 0f, nb = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (float) (Math.sqrt(na) * Math.sqrt(nb));
    }
}
