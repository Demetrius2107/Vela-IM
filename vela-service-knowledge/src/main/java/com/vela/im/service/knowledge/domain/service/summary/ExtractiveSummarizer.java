package com.vela.im.service.knowledge.domain.service.summary;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 抽取式摘要（默认实现）：
 * 1. 按句号/问号/感叹号等切句；
 * 2. 中文按单字、英文按单词统计词频（去掉停用字）；
 * 3. 每句打分 = 句内词频和 / sqrt(句长)，抑制超长句虚高；
 * 4. 取 Top-3 关键句，按原文顺序拼接，控制总长。
 */
@Component
public class ExtractiveSummarizer implements Summarizer {

    private static final int MAX_SENTENCES = 3;
    private static final int MAX_LENGTH = 200;

    private static final Set<Character> STOP_CHARS = new HashSet<>();
    private static final Set<String> STOP_WORDS = new HashSet<>();

    static {
        for (char c : "的了是在有和与及或我们你我他她它们这那一个不也都很把被对".toCharArray()) {
            STOP_CHARS.add(c);
        }
        for (String w : new String[]{"the", "a", "an", "and", "or", "of", "to", "is", "are", "was", "were", "in", "on", "for", "with"}) {
            STOP_WORDS.add(w);
        }
    }

    @Override
    public String summarize(String content) {
        if (content == null || content.trim().isEmpty()) return "";
        String text = content.trim();
        if (text.length() <= MAX_LENGTH) return text; // 短文直接作摘要

        List<String> sentences = splitSentences(text);
        if (sentences.isEmpty()) return text.substring(0, MAX_LENGTH);

        Map<String, Integer> tf = termFreq(sentences);
        List<ScoredSentence> scored = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i);
            if (s.trim().length() < 8) continue; // 过滤过短片段（标题行/空行残留）
            scored.add(new ScoredSentence(i, s, score(s, tf)));
        }
        scored.sort((a, b) -> Float.compare(b.score, a.score));
        List<ScoredSentence> top = scored.size() > MAX_SENTENCES ? scored.subList(0, MAX_SENTENCES) : scored;
        top.sort((a, b) -> Integer.compare(a.index, b.index)); // 恢复原文顺序

        StringBuilder sb = new StringBuilder();
        for (ScoredSentence s : top) {
            if (sb.length() + s.text.length() > MAX_LENGTH) break;
            sb.append(s.text).append(" ");
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? text.substring(0, Math.min(MAX_LENGTH, text.length())) : result;
    }

    private List<String> splitSentences(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            buf.append(c);
            if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == '；' || c == '\n') {
                if (buf.toString().trim().length() > 0) out.add(buf.toString().trim());
                buf.setLength(0);
            }
        }
        if (buf.toString().trim().length() > 0) out.add(buf.toString().trim());
        return out;
    }

    private Map<String, Integer> termFreq(List<String> sentences) {
        Map<String, Integer> tf = new HashMap<>();
        for (String s : sentences) {
            for (String token : tokens(s)) {
                tf.merge(token, 1, Integer::sum);
            }
        }
        return tf;
    }

    private List<String> tokens(String s) {
        List<String> tokens = new ArrayList<>();
        String lower = s.toLowerCase();
        // 英文单词
        for (String w : lower.split("[^a-z0-9]+")) {
            if (w.length() >= 2 && !STOP_WORDS.contains(w)) tokens.add(w);
        }
        // 中文字符（去掉停用字）
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fa5 && !STOP_CHARS.contains(c)) {
                tokens.add(String.valueOf(c));
            }
        }
        return tokens;
    }

    private float score(String sentence, Map<String, Integer> tf) {
        float sum = 0f;
        for (String t : tokens(sentence)) {
            Integer c = tf.get(t);
            if (c != null) sum += c;
        }
        int len = Math.max(sentence.length(), 1);
        return sum / (float) Math.sqrt(len);
    }

    private static class ScoredSentence {
        final int index;
        final String text;
        final float score;

        ScoredSentence(int index, String text, float score) {
            this.index = index;
            this.text = text;
            this.score = score;
        }
    }
}
