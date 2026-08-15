package com.vela.im.service.message.domain.strategy;

import com.alibaba.fastjson.JSONObject;
import com.vela.im.codec.pack.message.RecallMessageNotifyPack;
import com.vela.im.service.common.utils.ConversationIdGenerate;
import com.vela.im.service.common.utils.MessageProducer;
import com.vela.im.service.message.application.facade.ConversationFacade;
import com.vela.im.service.common.infrastructure.seq.RedisSeq;
import com.vela.im.service.common.utils.SnowflakeIdWorker;
import com.vela.im.shared.types.ClientInfo;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.types.enums.ConversationTypeEnum;
import com.vela.im.shared.types.enums.DelFlagEnum;
import com.vela.im.shared.types.enums.command.MessageCommand;
import com.vela.im.shared.types.message.OfflineMessageContent;
import com.vela.im.shared.types.message.RecallMessageContent;
import com.vela.im.service.message.domain.entity.ImMessageBodyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * <p>Title: P2PRecallStrategy</p>
 * <p>Description: 单聊消息撤回策略，将撤回通知写入双方离线队列并通知发送方其他设备和接收方。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-07-24
 */
@Component
public class P2PRecallStrategy implements RecallStrategy {

    private static final Logger logger = LoggerFactory.getLogger(P2PRecallStrategy.class);

    private final MessageProducer messageProducer;
    private final ConversationFacade conversationFacade;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisSeq redisSeq;

    public P2PRecallStrategy(MessageProducer messageProducer,
                             ConversationFacade conversationFacade,
                             RedisTemplate<String, String> redisTemplate,
                             RedisSeq redisSeq) {
        this.messageProducer = messageProducer;
        this.conversationFacade = conversationFacade;
        this.redisTemplate = redisTemplate;
        this.redisSeq = redisSeq;
    }

    @Override
    public void recall(RecallMessageContent content, RecallMessageNotifyPack pack,
                       ImMessageBodyEntity body) {
        // 构建双方 Redis 离线队列 Key
        String fromKey = content.getAppId() + ":" + ImConstants.Redis.OFFLINE_MESSAGE
                + ":" + content.getFromId();
        String toKey = content.getAppId() + ":" + ImConstants.Redis.OFFLINE_MESSAGE
                + ":" + content.getToId();

        // 构建撤回通知的离线消息体
        OfflineMessageContent offline = new OfflineMessageContent();
        BeanUtils.copyProperties(content, offline);
        offline.setDelFlag(DelFlagEnum.DELETE.getCode());
        offline.setMessageKey(content.getMessageKey());
        offline.setConversationType(ConversationTypeEnum.P2P.getCode());
        offline.setConversationId(conversationFacade.convertConversationId(
                offline.getConversationType(), content.getFromId(), content.getToId()));
        offline.setMessageBody(body.getMessageBody());

        long seq = redisSeq.doGetSeq(content.getAppId() + ":" + ImConstants.Sequence.MESSAGE
                + ":" + ConversationIdGenerate.generateP2PId(content.getFromId(), content.getToId()));
        offline.setMessageSequence(seq);

        long newMessageKey = SnowflakeIdWorker.nextId();

        // 写入双方离线队列
        retryZAdd(fromKey, JSONObject.toJSONString(offline), newMessageKey, "from");
        retryZAdd(toKey, JSONObject.toJSONString(offline), newMessageKey, "to");

        // ACK + 通知
        messageProducer.sendToUser(content.getFromId(), MessageCommand.MSG_RECALL_ACK, pack, content);
        retrySend(content.getFromId(), MessageCommand.MSG_RECALL_NOTIFY, pack, content, "sender");
        retrySend(content.getToId(), MessageCommand.MSG_RECALL_NOTIFY, pack, content.getAppId(), "receiver");
    }

    private void retryZAdd(String key, String value, double score, String label) {
        for (int i = 0; i < 2; i++) {
            try {
                redisTemplate.opsForZSet().add(key, value, score);
                return;
            } catch (Exception e) {
                logger.warn("P2P recall ZAdd failed ({}) attempt {}/2: {}", label, i + 1, e.getMessage());
                if (i == 1) logger.error("P2P recall ZAdd exhausted ({})", label);
            }
        }
    }

    private void retrySend(String toId, MessageCommand command, Object data, Object clientInfo, String label) {
        for (int i = 0; i < 2; i++) {
            try {
                if (clientInfo instanceof Integer) {
                    messageProducer.sendToUser(toId, command, data, (Integer) clientInfo);
                } else {
                    messageProducer.sendToUserExceptClient(toId, command, data, (ClientInfo) clientInfo);
                }
                return;
            } catch (Exception e) {
                logger.warn("P2P recall send failed ({}) attempt {}/2: {}", label, i + 1, e.getMessage());
                if (i == 1) logger.error("P2P recall send exhausted ({})", label);
            }
        }
    }
}
