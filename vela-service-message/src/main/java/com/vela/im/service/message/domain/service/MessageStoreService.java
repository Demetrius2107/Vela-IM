package com.vela.im.service.message.domain.service;

import com.alibaba.fastjson.JSONObject;
import com.vela.im.service.message.application.facade.ConversationFacade;
import com.vela.im.service.common.entity.ImGroupMessageHistoryEntity;
import com.vela.im.service.common.entity.ImGroupMessageHistoryMapper;
import com.vela.im.service.message.domain.entity.ImMessageBodyEntity;
import com.vela.im.service.message.domain.entity.ImMessageHistoryEntity;
import com.vela.im.service.message.infrastructure.persistence.mapper.ImMessageBodyMapper;
import com.vela.im.service.message.infrastructure.persistence.mapper.ImMessageHistoryMapper;
import com.vela.im.service.message.infrastructure.elasticsearch.MessageIndexService;
import com.vela.im.service.common.utils.ServiceDegradationManager;
import com.vela.im.service.common.utils.SnowflakeIdWorker;
import com.vela.im.shared.config.ImServerProperties;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.trace.TraceIdContext;
import com.vela.im.shared.types.enums.ConversationTypeEnum;
import com.vela.im.shared.types.enums.DelFlagEnum;
import com.vela.im.shared.types.message.*;

import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**5
 * <p>Title: MessageStoreService</p>
 * <p>Description: 消息存储领域服务，负责消息的持久化存储、MQ 降级写入、离线消息 ZSet 管理及缓存操作。</p>
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
public class MessageStoreService {

    private static final Logger logger = LoggerFactory.getLogger(MessageStoreService.class);

    private final ImMessageHistoryMapper imMessageHistoryMapper;
    private final ImMessageBodyMapper imMessageBodyMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final ImGroupMessageHistoryMapper imGroupMessageHistoryMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConversationFacade conversationFacade;
    private final ImServerProperties appConfig;
    private final MessageCompensationStore compensationStore;
    private final ServiceDegradationManager degradationManager;
    private final MessageIndexService messageIndexService;

    /**
     * 构建 TraceId 透传的 MessagePostProcessor
     *
     * @return 消息后置处理器
     */
    private MessagePostProcessor buildTracePostProcessor() {
        return message -> {
            String traceId = TraceIdContext.get();
            if (traceId != null && !traceId.isEmpty()) {
                message.getMessageProperties().setHeader(ImConstants.TraceId.MQ_HEADER_NAME, traceId);
            }
            return message;
        };
    }

    public MessageStoreService(ImMessageHistoryMapper imMessageHistoryMapper,
                               ImMessageBodyMapper imMessageBodyMapper,
                               SnowflakeIdWorker snowflakeIdWorker,
                               ImGroupMessageHistoryMapper imGroupMessageHistoryMapper,
                               RabbitTemplate rabbitTemplate,
                               StringRedisTemplate stringRedisTemplate,
                               ConversationFacade conversationFacade,
                               ImServerProperties appConfig,
                               MessageCompensationStore compensationStore,
                               ServiceDegradationManager degradationManager,
                               MessageIndexService messageIndexService) {
        this.imMessageHistoryMapper = imMessageHistoryMapper;
        this.imMessageBodyMapper = imMessageBodyMapper;
        this.snowflakeIdWorker = snowflakeIdWorker;
        this.imGroupMessageHistoryMapper = imGroupMessageHistoryMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.conversationFacade = conversationFacade;
        this.appConfig = appConfig;
        this.compensationStore = compensationStore;
        this.degradationManager = degradationManager;
        this.messageIndexService = messageIndexService;
    }

    /**
     * 存储单聊消息，通过 MQ 异步发送到消息存储服务
     * <p>MQ 不可用时自动降级为同步直接写入 DB。</p>
     *
     * @param messageContent 单聊消息内容
     */
    @Transactional
    public void storeP2PMessage(MessageContent messageContent){
        ImMessageBody imMessageBodyEntity = extractMessageBody(messageContent);
        DoStoreP2PMessageDto dto = new DoStoreP2PMessageDto();
        dto.setMessageContent(messageContent);
        dto.setMessageBody(imMessageBodyEntity);
        messageContent.setMessageKey(imMessageBodyEntity.getMessageKey());

        // MQ 降级检测：已知不可用时跳过 MQ，直接走 DB
        if (!degradationManager.isMqAvailable()) {
            logger.warn("MQ is degraded, skipping MQ send for P2P message, msgKey={}",
                    imMessageBodyEntity.getMessageKey());
            storeP2PMessageDirectly(imMessageBodyEntity, messageContent);
            return;
        }

        try {
            // Send to MQ for async persistence
            rabbitTemplate.convertAndSend(ImConstants.RabbitMQ.STORE_P2P_MESSAGE, "",
                    JSONObject.toJSONString(dto), buildTracePostProcessor());
            degradationManager.reportMqSuccess();
        } catch (Exception e) {
            logger.error("MQ send failed for P2P message, fallback to direct DB write, msgKey={}, error={}",
                    imMessageBodyEntity.getMessageKey(), e.getMessage());
            degradationManager.reportMqFailure("storeP2PMessage");
            storeP2PMessageDirectly(imMessageBodyEntity, messageContent);
        }
        // Index to Elasticsearch
        messageIndexService.indexMessage(messageContent);
    }

    /**
     * Fallback: write P2P message directly to DB when MQ is unavailable.
     *
     * @param messageBody    message body
     * @param messageContent message content
     */
    @Transactional
    public void storeP2PMessageDirectly(ImMessageBody messageBody, MessageContent messageContent) {
        try {
            ImMessageBodyEntity bodyEntity = new ImMessageBodyEntity();
            BeanUtils.copyProperties(messageBody, bodyEntity);
            imMessageBodyMapper.insert(bodyEntity);
            List<ImMessageHistoryEntity> histories = extractToP2PMessageHistory(messageContent, bodyEntity);
            imMessageHistoryMapper.insertBatchSomeColumn(histories);
            logger.warn("P2P message persisted to DB (fallback), msgKey={}", messageBody.getMessageKey());
        } catch (Exception dbEx) {
            logger.error("P2P 消息降级写入 DB 也失败，加入补偿队列，msgKey={}, error={}",
                    messageBody.getMessageKey(), dbEx.getMessage());
            compensationStore.compensate(messageBody, messageContent);
        }
    }

    /**
     * 提取消息体，生成 messageKey（雪花算法）
     *
     * @param messageContent 消息内容
     * @return 消息体对象
     */
    public ImMessageBody extractMessageBody(MessageContent messageContent){
        ImMessageBody messageBody = new ImMessageBody();
        messageBody.setAppId(messageContent.getAppId());
        messageBody.setMessageKey(SnowflakeIdWorker.nextId());
        messageBody.setCreateTime(System.currentTimeMillis());
        messageBody.setSecurityKey("");
        messageBody.setExtra(messageContent.getExtra());
        messageBody.setDelFlag(DelFlagEnum.NORMAL.getCode());
        messageBody.setMessageTime(messageContent.getMessageTime());
        messageBody.setMessageBody(messageContent.getMessageBody());
        return messageBody;
    }

    /**
     * 提取单聊双方的消息历史记录
     * <p>生成发送方和接收方两条消息历史记录，用于后续查询。</p>
     *
     * @param messageContent      消息内容
     * @param imMessageBodyEntity 消息体实体
     * @return 消息历史记录列表（发送方 + 接收方）
     */
    public List<ImMessageHistoryEntity> extractToP2PMessageHistory(MessageContent messageContent,
                                                                   ImMessageBodyEntity imMessageBodyEntity){
        List<ImMessageHistoryEntity> list = new ArrayList<>();
        ImMessageHistoryEntity fromHistory = new ImMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent,fromHistory);
        fromHistory.setOwnerId(messageContent.getFromId());
        fromHistory.setMessageKey(imMessageBodyEntity.getMessageKey());
        fromHistory.setCreateTime(System.currentTimeMillis());

        ImMessageHistoryEntity toHistory = new ImMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent,toHistory);
        toHistory.setOwnerId(messageContent.getToId());
        toHistory.setMessageKey(imMessageBodyEntity.getMessageKey());
        toHistory.setCreateTime(System.currentTimeMillis());

        list.add(fromHistory);
        list.add(toHistory);
        return list;
    }

    /**
     * 存储群聊消息，通过 MQ 异步发送到消息存储服务
     * <p>MQ 不可用时自动降级为同步直接写入 DB。</p>
     *
     * @param messageContent 群聊消息内容
     */
    @Transactional
    public void storeGroupMessage(GroupChatMessageContent messageContent){
        ImMessageBody imMessageBody = extractMessageBody(messageContent);
        DoStoreGroupMessageDto dto = new DoStoreGroupMessageDto();
        dto.setMessageBody(imMessageBody);
        dto.setGroupChatMessageContent(messageContent);
        messageContent.setMessageKey(imMessageBody.getMessageKey());

        // MQ 降级检测
        if (!degradationManager.isMqAvailable()) {
            logger.warn("MQ is degraded, skipping MQ send for group message, msgKey={}",
                    imMessageBody.getMessageKey());
            storeGroupMessageDirectly(imMessageBody, messageContent);
            return;
        }

        try {
            rabbitTemplate.convertAndSend(ImConstants.RabbitMQ.STORE_GROUP_MESSAGE,
                    "",
                    JSONObject.toJSONString(dto), buildTracePostProcessor());
            degradationManager.reportMqSuccess();
        } catch (Exception e) {
            logger.error("MQ 发送群聊消息存储任务失败，降级直接写入 DB，msgKey={}, error={}",
                    imMessageBody.getMessageKey(), e.getMessage());
            degradationManager.reportMqFailure("storeGroupMessage");
            // Fallback: write directly to DB when MQ is unavailable
            storeGroupMessageDirectly(imMessageBody, messageContent);
        }
    }

    /**
     * Fallback: write group message directly to DB when MQ is unavailable.
     *
     * @param messageBody    message body
     * @param messageContent group message content
     */
    @Transactional
    public void storeGroupMessageDirectly(ImMessageBody messageBody, GroupChatMessageContent messageContent) {
        try {
            ImMessageBodyEntity bodyEntity = new ImMessageBodyEntity();
            BeanUtils.copyProperties(messageBody, bodyEntity);
            imMessageBodyMapper.insert(bodyEntity);
            ImGroupMessageHistoryEntity groupHistory = extractToGroupMessageHistory(messageContent, bodyEntity);
            imGroupMessageHistoryMapper.insert(groupHistory);
            logger.warn("Group message persisted to DB (fallback), msgKey={}", messageBody.getMessageKey());
        } catch (Exception dbEx) {
            logger.error("Group message fallback DB write also failed, adding to compensation queue, msgKey={}, error={}",
                    messageBody.getMessageKey(), dbEx.getMessage());
            compensationStore.compensate(messageBody, messageContent);
        }
    }

    /**
     * 提取群聊消息历史记录
     *
     * @param messageContent      群聊消息内容
     * @param messageBodyEntity   消息体实体
     * @return 群聊消息历史记录
     */
    public ImGroupMessageHistoryEntity extractToGroupMessageHistory(GroupChatMessageContent
                                                                     messageContent, ImMessageBodyEntity messageBodyEntity){
        ImGroupMessageHistoryEntity result = new ImGroupMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent,result);
        result.setGroupId(messageContent.getGroupId());
        result.setMessageKey(messageBodyEntity.getMessageKey());
        result.setCreateTime(System.currentTimeMillis());
        return result;
    }

    /**
     * 缓存消息内容到 Redis（防重），TTL 300 秒
     * <p>Redis 操作失败时自动重试 2 次，重试耗尽仍失败则仅记录警告（缓存丢失不影响业务正确性）。</p>
     *
     * @param appId     应用ID
     * @param messageId 消息ID
     * @param messageContent 消息内容
     */
    public void setMessageFromMessageIdCache(Integer appId, String messageId, Object messageContent){
        //appid : cache : messageId
        String key =appId + ":" + ImConstants.Redis.CACHE_MESSAGE + ":" + messageId;
        for (int retry = 0; retry < 2; retry++) {
            try {
                stringRedisTemplate.opsForValue().set(key, JSONObject.toJSONString(messageContent), 300, TimeUnit.SECONDS);
                return;
            } catch (Exception e) {
                logger.warn("setMessageFromMessageIdCache failed (attempt {}/2), messageId={}, error={}",
                        retry + 1, messageId, e.getMessage());
                if (retry == 1) {
                    logger.error("setMessageFromMessageIdCache exhausted, messageId={}", messageId);
                    return;
                }
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * 从缓存获取消息内容（防重校验）
     *
     * @param appId     应用ID
     * @param messageId 消息ID
     * @param clazz     返回类型
     * @param <T>       泛型类型
     * @return 消息内容，不存在返回 null
     */
    public <T> T getMessageFromMessageIdCache(Integer appId,
                                              String messageId, Class<T> clazz){
        //appid : cache : messageId
        String key = appId + ":" + ImConstants.Redis.CACHE_MESSAGE + ":" + messageId;
        String msg = stringRedisTemplate.opsForValue().get(key);
        if(StringUtils.isBlank(msg)){
            return null;
        }
        return JSONObject.parseObject(msg, clazz);
    }

    /**
     * 存储单人离线消息，ZSet 超限时降级写入 DB
     *
     * @param offlineMessage 离线消息内容
     */
    public void storeOfflineMessage(OfflineMessageContent offlineMessage){

        // Build Redis keys for sender and receiver offline queues
        String fromKey = offlineMessage.getAppId() + ":" + ImConstants.Redis.OFFLINE_MESSAGE + ":" + offlineMessage.getFromId();
        String toKey = offlineMessage.getAppId() + ":" + ImConstants.Redis.OFFLINE_MESSAGE + ":" + offlineMessage.getToId();

        // Redis 降级检测：已知不可用时直接写 DB
        if (!degradationManager.isRedisAvailable()) {
            logger.warn("Redis is degraded, writing offline message directly to DB, msgKey={}",
                    offlineMessage.getMessageKey());
            persistToMessageHistory(offlineMessage, offlineMessage.getFromId());
            persistToMessageHistory(offlineMessage, offlineMessage.getToId());
            return;
        }

        ZSetOperations<String, String> operations = stringRedisTemplate.opsForZSet();
        try {
            // Evict oldest if sender queue exceeds limit, persist to DB as fallback
            evictIfExceeded(operations, fromKey);
            offlineMessage.setConversationId(conversationFacade.convertConversationId(
                    ConversationTypeEnum.P2P.getCode(),offlineMessage.getFromId(),offlineMessage.getToId()
            ));
            // Insert into sender queue, scored by messageKey
            operations.add(fromKey,JSONObject.toJSONString(offlineMessage),
                    offlineMessage.getMessageKey());

            // Evict oldest if receiver queue exceeds limit
            evictIfExceeded(operations, toKey);

            offlineMessage.setConversationId(conversationFacade.convertConversationId(
                    ConversationTypeEnum.P2P.getCode(),offlineMessage.getToId(),offlineMessage.getFromId()
            ));
            // Insert into receiver queue
            operations.add(toKey,JSONObject.toJSONString(offlineMessage),
                    offlineMessage.getMessageKey());
            degradationManager.reportRedisSuccess();
        } catch (Exception e) {
            logger.error("Redis offline message store failed, falling back to DB, fromId={}, toId={}, msgKey={}, error={}",
                    offlineMessage.getFromId(), offlineMessage.getToId(),
                    offlineMessage.getMessageKey(), e.getMessage());
            degradationManager.reportRedisFailure("storeOfflineMessage");
            // Fallback: persist directly to DB when Redis is unavailable
            persistToMessageHistory(offlineMessage, offlineMessage.getFromId());
            persistToMessageHistory(offlineMessage, offlineMessage.getToId());
        }
        // Index to Elasticsearch
        messageIndexService.indexOfflineMessage(offlineMessage);
    }

    /**
     * Evict the oldest message from ZSet when capacity exceeded, persist to DB before removal.
     */
    private void evictIfExceeded(ZSetOperations<String, String> operations, String key) {
        Long size = operations.zCard(key);
        if (size == null || size <= appConfig.getOfflineMessageCount()) {
            return;
        }

        long excessCount = size - appConfig.getOfflineMessageCount();
        logger.warn("Offline message ZSet full, key={}, size={}, limit={}, evicting {} entries",
                key, size, appConfig.getOfflineMessageCount(), excessCount);

        // Batch fetch the oldest entries beyond the limit
        Set<ZSetOperations.TypedTuple<String>> evictSet = operations.rangeWithScores(key, 0, excessCount - 1);
        if (evictSet == null || evictSet.isEmpty()) {
            return;
        }

        long maxEvictedSeq = 0L;
        List<ImMessageHistoryEntity> batch = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : evictSet) {
            String value = tuple.getValue();
            Double score = tuple.getScore();
            if (value == null || score == null) continue;

            try {
                OfflineMessageContent msg = JSONObject.parseObject(value, OfflineMessageContent.class);
                ImMessageHistoryEntity history = new ImMessageHistoryEntity();
                history.setAppId(msg.getAppId());
                history.setFromId(msg.getFromId());
                history.setToId(msg.getToId());
                history.setOwnerId(extractOwnerIdFromKey(key));
                history.setMessageKey(msg.getMessageKey());
                history.setSequence(msg.getMessageSequence());
                history.setMessageRandom(msg.getMessageRandom());
                history.setMessageTime(msg.getMessageTime());
                history.setCreateTime(System.currentTimeMillis());
                batch.add(history);

                if (score.longValue() > maxEvictedSeq) {
                    maxEvictedSeq = score.longValue();
                }
            } catch (Exception e) {
                logger.warn("Failed to parse evicted offline message, key={}, error={}", key, e.getMessage());
            }
        }

        // Batch persist to DB
        if (!batch.isEmpty()) {
            try {
                imMessageHistoryMapper.insertBatchSomeColumn(batch);
                logger.info("Batch evicted {} offline messages to DB, key={}", batch.size(), key);
            } catch (Exception e) {
                logger.error("Batch evict to DB failed, key={}, error={}", key, e.getMessage());
                // Fallback: insert one by one
                for (ImMessageHistoryEntity entity : batch) {
                    try { imMessageHistoryMapper.insert(entity); } catch (Exception e2) {
                        logger.error("Fallback insert failed, msgKey={}, error={}", entity.getMessageKey(), e2.getMessage());
                    }
                }
            }
        }

        // Remove the evicted entries from ZSet in one call
        operations.removeRange(key, 0, excessCount - 1);

        // Store eviction watermark: max sequence of evicted messages
        if (maxEvictedSeq > 0) {
            String watermarkKey = key.replace(ImConstants.Redis.OFFLINE_MESSAGE,
                    ImConstants.Redis.OFFLINE_EVICTED_WATERMARK);
            stringRedisTemplate.opsForValue().set(watermarkKey, String.valueOf(maxEvictedSeq),
                    7, TimeUnit.DAYS); // 7 day TTL
        }
    }

    /**
     * Persist offline message to message history table as fallback.
     */
    private void persistToMessageHistory(OfflineMessageContent msg, String ownerId) {
        try {
            ImMessageHistoryEntity history = new ImMessageHistoryEntity();
            history.setAppId(msg.getAppId());
            history.setFromId(msg.getFromId());
            history.setToId(msg.getToId());
            history.setOwnerId(ownerId);
            history.setMessageKey(msg.getMessageKey());
            history.setSequence(msg.getMessageSequence());
            history.setMessageRandom(msg.getMessageRandom());
            history.setMessageTime(msg.getMessageTime());
            history.setCreateTime(System.currentTimeMillis());
            imMessageHistoryMapper.insert(history);
        } catch (Exception e) {
            logger.error("Failed to persist offline message to DB, msgKey={}, ownerId={}, error={}",
                    msg.getMessageKey(), ownerId, e.getMessage());
        }
    }

    /**
     * Extract ownerId from Redis key.
     * Key format: appId:offlineMessage:ownerId
     */
    private String extractOwnerIdFromKey(String key) {
        String[] parts = key.split(":");
        if (parts.length >= 3) {
            return parts[2];
        }
        return "unknown";
    }


    /**
     * 存储群离线消息，ZSet 超限时降级写入 DB
     *
     * @param offlineMessage 离线消息内容
     * @param memberIds      群成员ID列表
     */
    public void storeGroupOfflineMessage(OfflineMessageContent offlineMessage, List<String> memberIds){

        ZSetOperations<String, String> operations = stringRedisTemplate.opsForZSet();
        offlineMessage.setConversationType(ConversationTypeEnum.GROUP.getCode());

        for (String memberId : memberIds) {
            // Build Redis key for each group member's offline queue
            String toKey = offlineMessage.getAppId() + ":" +
                    ImConstants.Redis.OFFLINE_MESSAGE + ":" +
                    memberId;
            offlineMessage.setConversationId(conversationFacade.convertConversationId(
                    ConversationTypeEnum.GROUP.getCode(),memberId,offlineMessage.getToId()
            ));
            try {
                evictIfExceeded(operations, toKey);
                // Insert into member's queue, scored by messageKey
                operations.add(toKey,JSONObject.toJSONString(offlineMessage),
                        offlineMessage.getMessageKey());
            } catch (Exception e) {
                logger.error("Redis group offline message store failed, falling back to DB, memberId={}, groupId={}, error={}",
                        memberId, offlineMessage.getToId(), e.getMessage());
                persistToMessageHistory(offlineMessage, memberId);
            }
        }
    }

}
