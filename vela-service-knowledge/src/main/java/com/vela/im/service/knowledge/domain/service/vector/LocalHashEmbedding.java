package com.vela.im.service.knowledge.domain.service.vector;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地哈希词袋 Embedding（默认实现，零外部依赖）。
 * 中文按双字组合（bigram）打词，英文按单词打词，hash 到固定维度并做 L2 归一化。
 * 效果弱于语义模型，但保证离线可用、确定性输出；接入真实 Embedding 时替换 Bean 即可。
 */
@Component
public class LocalHashEmbedding implements EmbeddingProvider {

    private static final int DIM = 256;

    @Override
    public int dimension() {
        return DIM;
    }

    @Override
    public float[] embed(String text) {
        float[] vec = new float[DIM];
        if (text == null || text.isEmpty()) return vec;
        String lower = text.toLowerCase();
        // 英文单词
        for (String token : lower.split("[^a-z0-9]+")) {
            if (!token.isEmpty()) add(vec, token);
        }
        // 中文 bigram
        List<String> grams = chineseBigrams(lower);
        for (String g : grams) add(vec, g);
        return normalize(vec);
    }

    private List<String> chineseBigrams(String lower) {
        List<String> grams = new ArrayList<>();
        StringBuilder cn = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fa5) cn.append(c);
            else {
                flush(cn, grams);
            }
        }
        flush(cn, grams);
        return grams;
    }

    private void flush(StringBuilder cn, List<String> grams) {
        if (cn.length() == 0) return;
        if (cn.length() == 1) {
            grams.add(cn.toString());
        } else {
            for (int i = 0; i + 1 < cn.length(); i++) {
                grams.add(cn.substring(i, i + 2));
            }
        }
        cn.setLength(0);
    }

    private void add(float[] vec, String token) {
        int idx = Math.floorMod(token.hashCode(), DIM);
        vec[idx] += 1f;
    }

    private float[] normalize(float[] vec) {
        float norm = 0f;
        for (float v : vec) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 1e-6f) {
            for (int i = 0; i < vec.length; i++) vec[i] /= norm;
        }
        return vec;
    }
}
