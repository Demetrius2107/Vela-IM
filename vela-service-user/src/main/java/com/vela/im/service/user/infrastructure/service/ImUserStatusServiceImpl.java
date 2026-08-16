package com.vela.im.service.user.infrastructure.service;

import com.vela.im.service.user.application.dto.UserStatusChangeNotifyContent;
import com.vela.im.service.user.application.dto.req.PullFriendOnlineStatusReq;
import com.vela.im.service.user.application.dto.req.PullUserOnlineStatusReq;
import com.vela.im.service.user.application.dto.req.SetUserCustomerStatusReq;
import com.vela.im.service.user.application.dto.req.SubscribeUserOnlineStatusReq;
import com.vela.im.service.user.application.dto.resp.UserOnlineStatusResp;
import com.vela.im.service.user.domain.service.ImUserStatusService;
import com.vela.im.service.common.utils.MessageProducer;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.types.enums.command.UserEventCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>Title: ImUserStatusServiceImpl</p>
 * <p>Description: 用户在线状态服务实现，基于 Redis 存储用户上下线状态、订阅关系与自定义状态。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.0
 * @createTime 2026-07-29
 * @updateTime 2026-08-16
 */
@Service
public class ImUserStatusServiceImpl implements ImUserStatusService {

    private static final Logger logger = LoggerFactory.getLogger(ImUserStatusServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final MessageProducer messageProducer;

    public ImUserStatusServiceImpl(StringRedisTemplate stringRedisTemplate,
                                   MessageProducer messageProducer) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.messageProducer = messageProducer;
    }

    @Override
    public void processUserOnlineStatusNotify(UserStatusChangeNotifyContent content) {
        if (content == null || content.getUserId() == null) return;

        String redisKey = content.getAppId() + ImConstants.Redis.USER_SESSION_PREFIX + content.getUserId();

        if (content.getStatus() == 1) {
            // 上线：写入 Redis，设置过期时间（30 分钟无心跳自动离线）
            stringRedisTemplate.opsForValue().set(redisKey, "online", 30, TimeUnit.MINUTES);
            logger.info("User online: userId={}, appId={}", content.getUserId(), content.getAppId());
        } else {
            // 下线：删除 Redis 中的在线标记
            stringRedisTemplate.delete(redisKey);
            logger.info("User offline: userId={}, appId={}", content.getUserId(), content.getAppId());
        }

        // 状态变更后，通知所有订阅了该用户在线状态的用户（反向订阅索引）
        notifySubscribers(content);
    }

    /**
     * 用户订阅好友在线状态通知：维护正向订阅（订阅者→被订阅者集合）与反向订阅（被订阅者→订阅者集合）索引。
     * 后续该用户上下线时，通过反向索引找到订阅者并推送状态变更。
     *
     * @param req 订阅请求（subUserId 为被订阅用户集合）
     */
    @Override
    public void subscribeUserOnlineStatus(SubscribeUserOnlineStatusReq req) {
        if (req == null || req.getSubUserId() == null || req.getOperater() == null) return;

        String subKey = req.getAppId() + ImConstants.Redis.USER_SUBSCRIBE_PREFIX + req.getOperater();
        // 订阅者视角：覆盖式重写订阅集合
        stringRedisTemplate.delete(subKey);
        if (!req.getSubUserId().isEmpty()) {
            stringRedisTemplate.opsForSet().add(subKey, req.getSubUserId().toArray(new String[0]));
        }

        // 反向索引：每个被订阅用户维护"谁订阅了我"，用于状态变更时精准推送
        for (String targetUserId : req.getSubUserId()) {
            String reverseKey = req.getAppId() + ImConstants.Redis.USER_SUBSCRIBED_PREFIX + targetUserId;
            stringRedisTemplate.opsForSet().add(reverseKey, req.getOperater());
        }
        logger.info("User subscribe status: subscriber={}, targets={}", req.getOperater(), req.getSubUserId());
    }

    /**
     * 设置用户自定义状态（如"忙碌/离开"及自定义文案），覆盖写入 Redis。
     *
     * @param req 自定义状态请求
     */
    @Override
    public void setUserCustomerStatus(SetUserCustomerStatusReq req) {
        if (req == null || req.getUserId() == null) return;

        String key = req.getAppId() + ImConstants.Redis.USER_CUSTOM_STATUS_PREFIX + req.getUserId();
        String value = req.getCustomStatus() + "|" + (req.getCustomText() == null ? "" : req.getCustomText());
        stringRedisTemplate.opsForValue().set(key, value);
        logger.info("User set custom status: userId={}, status={}, text={}",
                req.getUserId(), req.getCustomStatus(), req.getCustomText());
    }

    /**
     * 查询好友在线状态：基于请求中的 userList，逐个读取在线标记与自定义状态。
     *
     * @param req 好友状态查询请求
     * @return 用户 ID → 在线状态结果
     */
    @Override
    public Map<String, UserOnlineStatusResp> queryFriendOnlineStatus(PullFriendOnlineStatusReq req) {
        if (req == null || req.getUserList() == null) {
            return new HashMap<>();
        }
        return queryStatus(req.getAppId(), req.getUserList());
    }

    @Override
    public Map<String, UserOnlineStatusResp> queryUserOnlineStatus(PullUserOnlineStatusReq req) {
        if (req == null || req.getUserList() == null) {
            return new HashMap<>();
        }
        return queryStatus(req.getAppId(), req.getUserList());
    }

    /**
     * 批量查询用户在线状态与自定义状态（好友/用户查询共用）
     *
     * @param appId    应用 ID
     * @param userList 待查询用户 ID 集合
     * @return 用户 ID → 在线状态结果
     */
    private Map<String, UserOnlineStatusResp> queryStatus(Integer appId, List<String> userList) {
        Map<String, UserOnlineStatusResp> result = new HashMap<>();
        for (String userId : userList) {
            UserOnlineStatusResp resp = new UserOnlineStatusResp();
            // 在线标记：存在且值为 online 视为在线
            String sessionKey = appId + ImConstants.Redis.USER_SESSION_PREFIX + userId;
            String statusStr = stringRedisTemplate.opsForValue().get(sessionKey);
            resp.setConnectState("online".equals(statusStr) ? 1 : 0);

            // 自定义状态：status|customText
            String customKey = appId + ImConstants.Redis.USER_CUSTOM_STATUS_PREFIX + userId;
            String customValue = stringRedisTemplate.opsForValue().get(customKey);
            if (StringUtils.hasText(customValue)) {
                String[] parts = customValue.split("\\|", 2);
                resp.setCustomStatus(Integer.valueOf(parts[0]));
                if (parts.length > 1) {
                    resp.setCustomText(parts[1]);
                }
            }
            result.put(userId, resp);
        }
        return result;
    }

    /**
     * 状态变更后推送通知给所有订阅者（基于反向订阅索引）
     *
     * @param content 状态变更内容
     */
    private void notifySubscribers(UserStatusChangeNotifyContent content) {
        String reverseKey = content.getAppId() + ImConstants.Redis.USER_SUBSCRIBED_PREFIX + content.getUserId();
        Set<String> subscribers = stringRedisTemplate.opsForSet().members(reverseKey);
        if (subscribers == null || subscribers.isEmpty()) return;

        for (String subscriber : subscribers) {
            try {
                messageProducer.sendToUser(subscriber, UserEventCommand.USER_ONLINE_STATUS_CHANGE,
                        content, content.getAppId());
                logger.info("Notify status change to subscriber: target={}, subscriber={}",
                        content.getUserId(), subscriber);
            } catch (Exception e) {
                logger.warn("Notify status change failed: target={}, subscriber={}, error={}",
                        content.getUserId(), subscriber, e.getMessage());
            }
        }
    }
}
