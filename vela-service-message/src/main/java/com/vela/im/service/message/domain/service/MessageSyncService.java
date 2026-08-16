package com.vela.im.service.message.domain.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;

import com.vela.im.service.message.application.facade.ConversationFacade;
import com.vela.im.service.message.interfaces.feign.GroupServiceFeignClient;
import com.vela.im.service.message.domain.entity.ImMessageBodyEntity;
import com.vela.im.service.message.domain.entity.ImMessageHistoryEntity;
import com.vela.im.service.message.domain.strategy.GroupRecallStrategy;
import com.vela.im.service.message.domain.strategy.P2PRecallStrategy;
import com.vela.im.service.message.domain.strategy.RecallStrategy;
import com.vela.im.service.message.infrastructure.persistence.mapper.ImMessageBodyMapper;
import com.vela.im.service.message.infrastructure.persistence.mapper.ImMessageHistoryMapper;
import com.vela.im.service.common.infrastructure.seq.RedisSeq;
import com.vela.im.service.common.utils.ConversationIdGenerate;
import com.vela.im.service.message.domain.utils.GroupMessageProducer;
import com.vela.im.service.common.utils.MessageProducer;
import com.vela.im.service.common.utils.PendingAckTracker;
import com.vela.im.service.common.utils.MessageLockManager;
import com.vela.im.service.common.utils.SnowflakeIdWorker;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.config.ImServerProperties;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.types.enums.ConversationTypeEnum;
import com.vela.im.shared.types.enums.DelFlagEnum;
import com.vela.im.shared.types.enums.MessageErrorCode;
import com.vela.im.shared.types.enums.command.Command;
import com.vela.im.shared.types.enums.command.GroupEventCommand;
import com.vela.im.shared.types.enums.command.MessageCommand;
import com.vela.im.shared.types.ClientInfo;
import com.vela.im.shared.types.SyncReq;
import com.vela.im.shared.types.SyncResp;
import com.vela.im.shared.types.message.MessageReadedContent;
import com.vela.im.shared.types.message.MessageReceiveAckContent;
import com.vela.im.shared.types.message.OfflineMessageContent;
import com.vela.im.shared.types.message.RecallMessageContent;
import com.vela.im.codec.pack.message.MessageReadedPack;
import com.vela.im.codec.pack.message.RecallMessageNotifyPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Title: MessageSyncService</p>
 * <p>Description: 消息同步与已读回执服务，处理多端消息同步、已读回执、离线消息拉取、消息撤回等核心同步逻辑。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-06
 * @updateTime 2026-07-20
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
@Service
public class MessageSyncService {

    private final MessageProducer messageProducer;
    private final ConversationFacade conversationFacade;
    private final RedisTemplate<String, String> redisTemplate;
    private final ImMessageBodyMapper imMessageBodyMapper;
    private final RedisSeq redisSeq;
    private final GroupServiceFeignClient groupServiceFeignClient;
    private final GroupMessageProducer groupMessageProducer;
    private final ImServerProperties appConfig;
    private final PendingAckTracker pendingAckTracker;
    private final ImMessageHistoryMapper imMessageHistoryMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MessageLockManager messageLockManager;
    private final MessageReadService messageReadService;

    private final RecallStrategy p2pRecallStrategy;
    private final RecallStrategy groupRecallStrategy;

    private static final Logger logger = LoggerFactory.getLogger(MessageSyncService.class);

    /**
     * Simple retry with fixed delay for recall operations.
     * Keeps lock-holding time short (2 attempts only).
     */
    private <T> T retryOnce(Runnable operation, String operationName, T fallback) {
        try {
            operation.run();
            return null;
        } catch (Exception e) {
            logger.warn("{} failed, retrying once, error={}", operationName, e.getMessage());
            try {
                Thread.sleep(50L);
                operation.run();
                return null;
            } catch (Exception e2) {
                logger.error("{} retry also failed, error={}", operationName, e2.getMessage());
                return fallback;
            }
        }
    }

    public MessageSyncService(MessageProducer messageProducer,
                              ConversationFacade conversationFacade,
                              RedisTemplate<String, String> redisTemplate,
                              ImMessageBodyMapper imMessageBodyMapper,
                              RedisSeq redisSeq,
                              GroupServiceFeignClient groupServiceFeignClient,
                              GroupMessageProducer groupMessageProducer,
                              ImServerProperties appConfig,
                              P2PRecallStrategy p2pRecallStrategy,
                              GroupRecallStrategy groupRecallStrategy,
                              PendingAckTracker pendingAckTracker,
                              ImMessageHistoryMapper imMessageHistoryMapper,
                              StringRedisTemplate stringRedisTemplate,
                              MessageLockManager messageLockManager,
                              MessageReadService messageReadService) {
        this.messageProducer = messageProducer;
        this.conversationFacade = conversationFacade;
        this.groupServiceFeignClient = groupServiceFeignClient;
        this.redisTemplate = redisTemplate;
        this.imMessageBodyMapper = imMessageBodyMapper;
        this.redisSeq = redisSeq;
        this.groupMessageProducer = groupMessageProducer;
        this.appConfig = appConfig;
        this.p2pRecallStrategy = p2pRecallStrategy;
        this.groupRecallStrategy = groupRecallStrategy;
        this.pendingAckTracker = pendingAckTracker;
        this.imMessageHistoryMapper = imMessageHistoryMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.messageLockManager = messageLockManager;
        this.messageReadService = messageReadService;
    }

    /**
     * Forward receive ACK from receiver to sender, and clear pending ACK tracker.
     *
     * @param messageReceiveAckContent receive ACK content
     */
    public void receiveMark(MessageReceiveAckContent messageReceiveAckContent){
        if (messageReceiveAckContent == null || StringUtils.isBlank(messageReceiveAckContent.getToId())) {
            logger.warn("receiveMark skipped: invalid content");
            return;
        }
        // Clear from pending ACK tracker (receiver confirmed delivery)
        pendingAckTracker.acknowledge(messageReceiveAckContent.getFromId(), messageReceiveAckContent.getMessageKey());
        // Forward the ACK to the original sender
        messageProducer.sendToUser(messageReceiveAckContent.getToId(),
                MessageCommand.MSG_RECIVE_ACK,messageReceiveAckContent,messageReceiveAckContent.getAppId());
    }

    /**
     * Handle P2P message read receipt.
     * <p>Updates conversation read sequence → notifies sender's other devices → sends read receipt to the message originator.</p>
     *
     * @param messageContent read receipt content
     */
    public void readMark(MessageReadedContent messageContent) {
        conversationFacade.messageMarkRead(messageContent);
        MessageReadedPack messageReadedPack = new MessageReadedPack();
        BeanUtils.copyProperties(messageContent,messageReadedPack);
        syncToSender(messageReadedPack,messageContent,MessageCommand.MSG_READED_NOTIFY);
        // Send read receipt to the message originator
        messageProducer.sendToUser(messageContent.getToId(),
                MessageCommand.MSG_READED_RECEIPT,messageReadedPack,messageContent.getAppId());
    }

    /**
     * Sync read receipt to sender's other online devices.
     *
     * @param pack    read receipt notification pack
     * @param content read receipt content
     * @param command command type
     */
    private void syncToSender(MessageReadedPack pack, MessageReadedContent content, Command command){
        // Send to sender's other devices (excluding current)
        messageProducer.sendToUserExceptClient(pack.getFromId(),
                command,pack,
                content);
    }

    /**
     * Handle group message read receipt.
     * <p>Updates conversation read sequence → records read member → syncs to sender's other devices → sends receipt to the message originator.</p>
     *
     * @param messageReaded read receipt content
     */
    public void groupReadMark(MessageReadedContent messageReaded) {
        conversationFacade.messageMarkRead(messageReaded);
        // Record which group member read this message (for read/unread member list)
        if (messageReaded.getMessageKey() != null && messageReaded.getAppId() != null) {
            try {
                messageReadService.markRead(messageReaded.getAppId(),
                        messageReaded.getGroupId(), messageReaded.getMessageKey(), messageReaded.getFromId());
            } catch (Exception e) {
                logger.warn("Record read member failed, msgKey={}, memberId={}, error={}",
                        messageReaded.getMessageKey(), messageReaded.getFromId(), e.getMessage());
            }
        }
        MessageReadedPack messageReadedPack = new MessageReadedPack();
        BeanUtils.copyProperties(messageReaded,messageReadedPack);
        syncToSender(messageReadedPack,messageReaded, GroupEventCommand.MSG_GROUP_READED_NOTIFY
        );
        if(!messageReaded.getFromId().equals(messageReaded.getToId())){
            messageProducer.sendToUser(messageReadedPack.getToId(),GroupEventCommand.MSG_GROUP_READED_RECEIPT
                    ,messageReaded,messageReaded.getAppId());
        }
    }

    /**
     * Sync offline messages (incremental pull).
     * <p>Fetches offline messages from Redis ZSet by sequence range.
     * When messages have been evicted from ZSet due to capacity limits,
     * also queries the DB message history table to fill the gap.</p>
     *
     * @param req sync request (with lastSequence / maxLimit)
     * @return sync response (with offline message list and max sequence)
     */
    public Result syncOfflineMessage(SyncReq req) {

        SyncResp<OfflineMessageContent> resp = new SyncResp<>();

        // Boundary check: validate request parameters
        if (req == null || req.getAppId() == null || StringUtils.isBlank(req.getOperater())) {
            logger.warn("syncOfflineMessage skipped: invalid request (appId or operater is empty)");
            resp.setMaxSequence(0L);
            resp.setDataList(new ArrayList<>());
            resp.setCompleted(true);
            return Result.ok(resp);
        }

        // Clamp maxLimit to safe bounds [1, 500]
        if (req.getMaxLimit() == null || req.getMaxLimit() <= 0) {
            req.setMaxLimit(100);
        } else if (req.getMaxLimit() > 500) {
            req.setMaxLimit(500);
        }

        // Clamp lastSequence to non-negative
        if (req.getLastSequence() == null || req.getLastSequence() < 0) {
            req.setLastSequence(0L);
        }

        String offlineKey = req.getAppId() + ":" + ImConstants.Redis.OFFLINE_MESSAGE + ":" + req.getOperater();
        String watermarkKey = req.getAppId() + ":" + ImConstants.Redis.OFFLINE_EVICTED_WATERMARK + ":" + req.getOperater();

        try {
            // Fetch the max sequence from the ZSet
            long maxSeq = 0L;
            ZSetOperations<String, String> zSetOperations = redisTemplate.opsForZSet();
            Set<ZSetOperations.TypedTuple<String>> set = zSetOperations.reverseRangeWithScores(offlineKey, 0, 0);
            if (!CollectionUtils.isEmpty(set)) {
                ZSetOperations.TypedTuple<String> typedTuple = set.iterator().next();
                Double score = typedTuple.getScore();
                if (score != null) {
                    maxSeq = score.longValue();
                }
            }

            // Use a Set to deduplicate by messageKey
            Set<Long> seenKeys = new HashSet<>();
            List<OfflineMessageContent> respList = new ArrayList<>();
            resp.setMaxSequence(maxSeq);

            // Step 1: Query messages from ZSet (current offline queue)
            Set<ZSetOperations.TypedTuple<String>> querySet = zSetOperations.rangeByScoreWithScores(offlineKey,
                    req.getLastSequence(), maxSeq, 0, req.getMaxLimit());
            if (querySet != null) {
                for (ZSetOperations.TypedTuple<String> typedTuple : querySet) {
                    String value = typedTuple.getValue();
                    if (value == null) continue;
                    try {
                        OfflineMessageContent offlineMsg = JSONObject.parseObject(value, OfflineMessageContent.class);
                        if (offlineMsg.getMessageKey() != null) {
                            seenKeys.add(offlineMsg.getMessageKey());
                        }
                        respList.add(offlineMsg);
                    } catch (Exception e) {
                        logger.warn("Failed to parse offline message from ZSet, error={}", e.getMessage());
                    }
                }
            }

            // Step 2: Check eviction watermark — if the client is catching up from before eviction,
            // also query DB history for messages that may have been evicted
            String watermarkStr = stringRedisTemplate.opsForValue().get(watermarkKey);
            if (watermarkStr != null) {
                try {
                    long evictedMaxSeq = Long.parseLong(watermarkStr);
                    // If client's lastSequence is below the watermark, some messages were evicted
                    if (req.getLastSequence() < evictedMaxSeq && respList.size() < req.getMaxLimit()) {
                        long historyLimit = req.getMaxLimit() - respList.size();
                        logger.info("Filling offline sync from DB history: operater={}, lastSeq={}, watermark={}, limit={}",
                                req.getOperater(), req.getLastSequence(), evictedMaxSeq, historyLimit);

                        QueryWrapper<ImMessageHistoryEntity> historyQuery = new QueryWrapper<>();
                        historyQuery.eq("owner_id", req.getOperater())
                                .eq("app_id", req.getAppId())
                                .gt("sequence", req.getLastSequence())
                                .le("sequence", evictedMaxSeq)
                                .orderByAsc("sequence")
                                .last("LIMIT " + historyLimit);
                        List<ImMessageHistoryEntity> historyList = imMessageHistoryMapper.selectList(historyQuery);

                        for (ImMessageHistoryEntity history : historyList) {
                            // Deduplicate against already-fetched messages
                            if (history.getMessageKey() != null && seenKeys.contains(history.getMessageKey())) {
                                continue;
                            }
                            OfflineMessageContent offlineMsg = new OfflineMessageContent();
                            offlineMsg.setAppId(history.getAppId());
                            offlineMsg.setFromId(history.getFromId());
                            offlineMsg.setToId(history.getToId());
                            offlineMsg.setMessageKey(history.getMessageKey());
                            offlineMsg.setMessageSequence(history.getSequence());
                            offlineMsg.setMessageRandom(history.getMessageRandom());
                            offlineMsg.setMessageTime(history.getMessageTime());
                            offlineMsg.setConversationType(ConversationTypeEnum.P2P.getCode());
                            respList.add(offlineMsg);
                            if (history.getMessageKey() != null) {
                                seenKeys.add(history.getMessageKey());
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid eviction watermark value: {}, key={}", watermarkStr, watermarkKey);
                }
            }

            resp.setDataList(respList);

            if (!CollectionUtils.isEmpty(respList)) {
                OfflineMessageContent offlineMessageContent = respList.get(respList.size() - 1);
                resp.setCompleted(maxSeq <= offlineMessageContent.getMessageKey());
            }
        } catch (Exception e) {
            logger.error("Redis offline message sync failed, fallback to empty list, userId={}, error={}",
                    req.getOperater(), e.getMessage());
            // Fallback: return empty list, client will fetch from HTTP history
            resp.setMaxSequence(0L);
            resp.setDataList(new ArrayList<>());
            resp.setCompleted(true);
        }

        return Result.ok(resp);
    }

    /**
     * Handle message recall request.
     * <p>Flow: time boundary check (with clock skew tolerance) → concurrent conflict protection → query message body → mark deleted → update offline message status → notify sync targets and receiver.</p>
     *
     * @param content recall request content
     */
    public void recallMessage(RecallMessageContent content) {

        // Boundary check: validate recall request parameters
        if (content == null) {
            logger.warn("recallMessage skipped: content is null");
            return;
        }
        if (content.getMessageKey() == null || content.getMessageKey() <= 0) {
            logger.warn("recallMessage skipped: invalid messageKey");
            return;
        }
        if (content.getMessageTime() == null || content.getMessageTime() <= 0) {
            logger.warn("recallMessage skipped: invalid messageTime");
            return;
        }
        if (StringUtils.isBlank(content.getFromId()) || StringUtils.isBlank(content.getToId())) {
            logger.warn("recallMessage skipped: invalid fromId or toId");
            return;
        }

        Long messageTime = content.getMessageTime();
        Long now = System.currentTimeMillis();

        RecallMessageNotifyPack pack = new RecallMessageNotifyPack();
        BeanUtils.copyProperties(content,pack);

        // 1. Time boundary check: use configurable recall timeout + configurable clock skew tolerance
        long recallTimeout = appConfig.getMessageRecallTimeOut() != null
                ? appConfig.getMessageRecallTimeOut() : 120000L;
        long clockSkewTolerance = appConfig.getMessageRecallClockSkewTolerance() != null
                ? appConfig.getMessageRecallClockSkewTolerance() : 5000L;

        // Reject if client time is far ahead of server (possible clock desync)
        if (messageTime > now + clockSkewTolerance) {
            logger.warn("Client clock skew detected (ahead), messageTime={}, serverTime={}, skew={}ms",
                    messageTime, now, messageTime - now);
            recallAck(pack, Result.fail(MessageErrorCode.MESSAGE_CLOCK_SKEW_EXCEEDED), content);
            return;
        }

        // Reject if client time is far behind server (backward clock skew)
        if (messageTime < now - recallTimeout - clockSkewTolerance) {
            logger.warn("Client clock skew detected (behind), messageTime={}, serverTime={}, skew={}ms",
                    messageTime, now, now - messageTime);
            recallAck(pack, Result.fail(MessageErrorCode.MESSAGE_CLOCK_SKEW_EXCEEDED), content);
            return;
        }

        // Check recall timeout against server time (authoritative)
        if (recallTimeout < now - messageTime) {
            logger.warn("Recall timed out, msgKey={}, messageTime={}, now={}, timeout={}",
                    content.getMessageKey(), messageTime, now, recallTimeout);
            recallAck(pack,Result.fail(MessageErrorCode.MESSAGE_RECALL_TIME_OUT),content);
            return;
        }

        // 2. Concurrent conflict protection: acquire write lock to prevent
        //    concurrent push/read while recalling this message
        Long messageKey = content.getMessageKey();
        if (!messageLockManager.tryWriteLock(messageKey)) {
            logger.warn("Recall write lock timeout, msgKey={}, possible concurrent push/read", messageKey);
            recallAck(pack, Result.fail(MessageErrorCode.MESSAGE_CONCURRENT_OPERATION), content);
            return;
        }
        try {
            QueryWrapper<ImMessageBodyEntity> query = new QueryWrapper<>();
            query.eq("app_id",content.getAppId());
            query.eq("message_key", content.getMessageKey());
            ImMessageBodyEntity body = imMessageBodyMapper.selectOne(query);

            if(body == null){
                logger.warn("Recall target not found, msgKey={}", content.getMessageKey());
                recallAck(pack,Result.fail(MessageErrorCode.MESSAGEBODY_IS_NOT_EXIST),content);
                return;
            }

            if(body.getDelFlag() == DelFlagEnum.DELETE.getCode()){
                logger.warn("Message already recalled, msgKey={}", content.getMessageKey());
                recallAck(pack,Result.fail(MessageErrorCode.MESSAGE_IS_RECALLED),content);
                return;
            }

            body.setDelFlag(DelFlagEnum.DELETE.getCode());
            imMessageBodyMapper.update(body,query);

            if(content.getConversationType() == ConversationTypeEnum.P2P.getCode()){
                p2pRecallStrategy.recall(content, pack, body);
            }else{
                groupRecallStrategy.recall(content, pack, body);
            }
        } finally {
            messageLockManager.unlockWrite(messageKey);
        }
    }
    /**
     * Send recall result ACK to the request initiator.
     *
     * @param recallPack recall notification pack
     * @param success    response result
     * @param clientInfo client info
     */
    private void recallAck(RecallMessageNotifyPack recallPack, Result<Object> success, ClientInfo clientInfo) {
        messageProducer.sendToUser(recallPack.getFromId(),
                MessageCommand.MSG_RECALL_ACK, success, clientInfo);
    }

}
