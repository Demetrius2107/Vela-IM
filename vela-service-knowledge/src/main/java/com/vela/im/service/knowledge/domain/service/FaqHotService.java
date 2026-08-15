package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FAQ 热榜服务。
 * 热度分 = readCount × 1 + favoriteCount × 5（收藏比阅读更有价值）；
 * 支持时间窗：近7天 / 近30天 / 全部（按 update_time）。
 */
@Service
public class FaqHotService {

    /** 收藏加权系数 */
    private static final int FAVORITE_WEIGHT = 5;

    private final DocumentMapper documentMapper;

    public FaqHotService(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    /**
     * 查询热榜。
     *
     * @param windowDays 时间窗天数：0=全部，7=近7天，30=近30天
     * @param limit      返回条数
     */
    public Result<List<Map<String, Object>>> hot(Integer appId, int windowDays, int limit) {
        int n = Math.min(Math.max(limit <= 0 ? 10 : limit, 1), 50);
        QueryWrapper<DocumentEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId)
                .eq("is_deleted", 0)
                .eq("status", DocumentService.STATUS_PUBLISHED);
        if (windowDays > 0) {
            long cutoff = System.currentTimeMillis() - windowDays * 24L * 3600L * 1000L;
            q.ge("update_time", cutoff);
        }
        q.last("ORDER BY (IFNULL(read_count,0) + IFNULL(favorite_count,0) * " + FAVORITE_WEIGHT
                + ") DESC, update_time DESC LIMIT " + n);
        List<DocumentEntity> list = documentMapper.selectList(q);

        List<Map<String, Object>> result = new ArrayList<>();
        for (DocumentEntity d : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("docId", d.getId());
            item.put("title", d.getTitle());
            item.put("summary", d.getSummary());
            item.put("readCount", d.getReadCount());
            item.put("favoriteCount", d.getFavoriteCount());
            item.put("hotScore", hotScore(d));
            item.put("updateTime", d.getUpdateTime());
            result.add(item);
        }
        return Result.ok(result);
    }

    private long hotScore(DocumentEntity d) {
        long read = d.getReadCount() == null ? 0 : d.getReadCount();
        long fav = d.getFavoriteCount() == null ? 0 : d.getFavoriteCount();
        return read + fav * FAVORITE_WEIGHT;
    }
}
