package com.vela.im.service.bot.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vela.im.service.bot.domain.entity.ImBotEntity;
import com.vela.im.service.bot.domain.entity.ImUserBotEntity;
import com.vela.im.service.bot.infrastructure.persistence.mapper.ImBotMapper;
import com.vela.im.service.bot.infrastructure.persistence.mapper.ImUserBotMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.BusinessErrorCode;
import com.vela.im.shared.types.enums.StatusConstants;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>Title: BotMarketService</p>
 * <p>Description: Bot 市场服务，提供市场列表、安装/卸载、我的Bot等功能。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.0
 * @createTime 2026-07-29
 */
@Service
public class BotMarketService {

    private final ImBotMapper botMapper;
    private final ImUserBotMapper userBotMapper;

    public BotMarketService(ImBotMapper botMapper, ImUserBotMapper userBotMapper) {
        this.botMapper = botMapper;
        this.userBotMapper = userBotMapper;
    }

    /**
     * 市场列表——列出所有已启用的公开 Bot，支持分类筛选和关键词搜索
     */
    public Result<List<ImBotEntity>> marketList(Integer appId, String category, String keyword) {
        QueryWrapper<ImBotEntity> qw = new QueryWrapper<>();
        qw.eq("status", StatusConstants.BOT_ENABLED);
        if (category != null && !category.isEmpty()) {
            qw.eq("category", category);
        }
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("bot_name", keyword).or().like("description", keyword));
        }
        qw.orderByDesc("id");
        List<ImBotEntity> list = botMapper.selectList(qw);
        return Result.ok(list);
    }

    /**
     * 获取市场分类列表（统计每个分类的 Bot 数量）
     */
    public Result<List<Map<String, Object>>> categories(Integer appId) {
        List<ImBotEntity> all = botMapper.selectList(
                new QueryWrapper<ImBotEntity>()
                        .eq("status", StatusConstants.BOT_ENABLED)
                        .isNotNull("category"));
        Map<String, Long> countMap = all.stream()
                .filter(b -> b.getCategory() != null && !b.getCategory().isEmpty())
                .collect(Collectors.groupingBy(ImBotEntity::getCategory, Collectors.counting()));

        List<Map<String, Object>> result = countMap.entrySet().stream()
                .map(e -> Map.<String, Object>of("category", e.getKey(), "count", e.getValue()))
                .collect(Collectors.toList());
        // 加一个"全部"入口
        result.add(0, Map.of("category", "全部", "count", all.size()));
        return Result.ok(result);
    }

    /**
     * 安装 Bot——创建用户-Bot 订阅关系
     */
    public Result<Void> install(Integer appId, String userId, String botId) {
        // 检查 Bot 是否存在并启用
        ImBotEntity bot = botMapper.selectOne(
                new QueryWrapper<ImBotEntity>().eq("bot_id", botId).eq("app_id", appId));
        if (bot == null) return Result.fail(BusinessErrorCode.BOT_NOT_FOUND);
        if (bot.getStatus() != StatusConstants.BOT_ENABLED) return Result.fail(BusinessErrorCode.BOT_DISABLED);

        // 检查是否已安装
        ImUserBotEntity existing = userBotMapper.selectOne(
                new QueryWrapper<ImUserBotEntity>()
                        .eq("user_id", userId)
                        .eq("bot_id", botId)
                        .eq("app_id", appId));
        if (existing != null) return Result.fail(BusinessErrorCode.BOT_ALREADY_INSTALLED);

        ImUserBotEntity sub = new ImUserBotEntity();
        sub.setAppId(appId);
        sub.setUserId(userId);
        sub.setBotId(botId);
        sub.setStatus(StatusConstants.BOT_ENABLED);
        sub.setCreateTime(System.currentTimeMillis());
        userBotMapper.insert(sub);
        return Result.ok();
    }

    /**
     * 卸载 Bot——删除订阅关系
     */
    public Result<Void> uninstall(Integer appId, String userId, String botId) {
        ImUserBotEntity existing = userBotMapper.selectOne(
                new QueryWrapper<ImUserBotEntity>()
                        .eq("user_id", userId)
                        .eq("bot_id", botId)
                        .eq("app_id", appId));
        if (existing == null) return Result.fail(BusinessErrorCode.BOT_INSTALL_NOT_FOUND);
        userBotMapper.deleteById(existing.getId());
        return Result.ok();
    }

    /**
     * 用户的 Bot 列表
     */
    public Result<List<ImBotEntity>> myBots(Integer appId, String userId) {
        List<ImUserBotEntity> subs = userBotMapper.selectList(
                new QueryWrapper<ImUserBotEntity>()
                        .eq("user_id", userId)
                        .eq("app_id", appId)
                        .eq("status", StatusConstants.BOT_ENABLED));
        if (subs.isEmpty()) return Result.ok(Collections.emptyList());

        List<String> botIds = subs.stream().map(ImUserBotEntity::getBotId).collect(Collectors.toList());
        List<ImBotEntity> bots = botMapper.selectList(
                new QueryWrapper<ImBotEntity>().in("bot_id", botIds));
        return Result.ok(bots);
    }

    /**
     * 检查用户是否安装了某个 Bot
     */
    public Result<Boolean> isInstalled(Integer appId, String userId, String botId) {
        Long count = (long) userBotMapper.selectCount(
                new QueryWrapper<ImUserBotEntity>()
                        .eq("user_id", userId)
                        .eq("bot_id", botId)
                        .eq("app_id", appId));
        return Result.ok(count > 0);
    }
}
