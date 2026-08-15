package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 机器人检索问答服务：从自然语言问题中抽取关键词，按字段加权评分检索 Top-N 候选，
 * 组装带引用出处的回答。P1 为检索式问答，P3 升级为 RAG 生成式问答。
 */
@Service
public class BotAskService {

    /** 问题停用词：疑问词/助词/代词，不参与检索 */
    private static final List<String> STOPWORDS = Arrays.asList(
            "如何", "怎么", "怎样", "为什么", "为啥", "什么", "哪些", "哪个", "请问",
            "一下", "一下下", "吗", "呢", "啊", "呀", "吧", "的", "了", "在", "是",
            "我", "你", "他", "她", "它", "们", "有", "没有", "可以", "能不能",
            "how", "what", "why", "when", "where", "which", "the", "a", "an", "to", "is", "are"
    );

    /** 字段权重：标题 > 摘要 > 标签 > 正文 */
    private static final int W_TITLE = 5;
    private static final int W_SUMMARY = 3;
    private static final int W_TAGS = 2;
    private static final int W_CONTENT = 1;

    private final DocumentMapper documentMapper;

    public BotAskService(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    /**
     * 问答检索入口：返回 Top-N 候选，每条含标题/摘要/链接/相关度分；无结果时返回引导话术。
     */
    public Result<Map<String, Object>> ask(Integer appId, String question, int limit) {
        if (question == null || question.trim().isEmpty()) {
            return Result.fail(com.vela.im.shared.types.enums.BusinessErrorCode.BAD_REQUEST);
        }
        int n = Math.min(Math.max(limit <= 0 ? 3 : limit, 1), 10);
        List<String> keywords = extractKeywords(question);

        QueryWrapper<DocumentEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId)
                .eq("is_deleted", 0)
                .eq("status", DocumentService.STATUS_PUBLISHED);
        if (keywords.isEmpty()) {
            // 无有效关键词（纯疑问句），退化为时间倒序取最新文档
            q.orderByDesc("update_time");
        } else {
            // 任一关键词命中即进入候选，再按命中数*权重打分排序
            q.and(w -> {
                for (String kw : keywords) {
                    w.like("title", kw).or().like("content", kw)
                            .or().like("summary", kw).or().like("tags", kw);
                }
            });
        }
        q.last("LIMIT " + (n * 10)); // 粗召回，再在应用层精排
        List<DocumentEntity> candidates = documentMapper.selectList(q);

        List<Map<String, Object>> scored = new ArrayList<>();
        for (DocumentEntity d : candidates) {
            int score = score(d, keywords);
            if (score > 0 || keywords.isEmpty()) {
                Map<String, Object> hit = new HashMap<>();
                hit.put("docId", d.getId());
                hit.put("title", d.getTitle());
                hit.put("summary", d.getSummary() == null ? "" : d.getSummary());
                hit.put("score", score);
                scored.add(hit);
            }
        }
        // 精排：分数降序，同分按更新时间
        scored.sort((a, b) -> {
            int byScore = (Integer) b.get("score") - (Integer) a.get("score");
            if (byScore != 0) return byScore;
            return ((String) b.get("title")).compareTo((String) a.get("title"));
        });
        List<Map<String, Object>> top = scored.size() > n ? scored.subList(0, n) : scored;

        Map<String, Object> result = new HashMap<>();
        result.put("question", question);
        result.put("keywords", keywords);
        result.put("hits", top);
        if (top.isEmpty()) {
            result.put("answer", "抱歉，知识库中暂时没有找到与「" + question + "」相关的内容。"
                    + "可以换个说法再问一次，或前往 FAQ 热榜查看常见问题。");
        } else {
            result.put("answer", "根据知识库找到 " + top.size() + " 篇相关文档，点击以下链接查看详情：");
        }
        return Result.ok(result);
    }

    /**
     * 抽取检索关键词：切英文单词 + 中文整句；过滤停用词与标点。
     */
    private List<String> extractKeywords(String question) {
        List<String> kws = new ArrayList<>();
        // 英文单词
        String lower = question.toLowerCase();
        for (String token : lower.split("[^a-z0-9]+")) {
            if (token.length() >= 2 && !STOPWORDS.contains(token)) kws.add(token);
        }
        // 中文：去掉标点/停用词后，按常见分隔符切分
        String cn = question.replaceAll("[\\p{P}\\p{S}\\s]+", "")
                .replaceAll("[A-Za-z0-9]", "");
        if (cn.length() >= 2) {
            for (String stop : STOPWORDS) {
                cn = cn.replace(stop, "|");
            }
            for (String seg : cn.split("\\|")) {
                if (seg.trim().length() >= 2) kws.add(seg.trim());
            }
        }
        return kws;
    }

    /** 字段加权评分：命中关键词数 × 字段权重，多关键词可累加 */
    private int score(DocumentEntity d, List<String> keywords) {
        if (keywords.isEmpty()) return 1;
        int total = 0;
        String title = d.getTitle() == null ? "" : d.getTitle().toLowerCase();
        String summary = d.getSummary() == null ? "" : d.getSummary().toLowerCase();
        String tags = d.getTags() == null ? "" : d.getTags().toLowerCase();
        String content = d.getContent() == null ? "" : d.getContent().toLowerCase();
        for (String kw : keywords) {
            if (title.contains(kw)) total += W_TITLE;
            if (summary.contains(kw)) total += W_SUMMARY;
            if (tags.contains(kw)) total += W_TAGS;
            if (content.contains(kw)) total += W_CONTENT;
        }
        return total;
    }
}
