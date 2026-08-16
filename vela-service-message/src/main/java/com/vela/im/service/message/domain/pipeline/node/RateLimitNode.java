package com.vela.im.service.message.domain.pipeline.node;

import com.vela.im.service.common.pipeline.MessageContext;
import com.vela.im.service.common.pipeline.PipeChain;
import com.vela.im.service.common.pipeline.PipeNode;
import com.vela.im.service.common.utils.MessageProducer;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.config.ImServerProperties;
import com.vela.im.shared.types.enums.MessageErrorCode;
import com.vela.im.shared.types.enums.command.MessageCommand;
import com.vela.im.shared.types.message.MessageContent;
import com.vela.im.codec.pack.message.ChatMessageAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * <p>Title: RateLimitNode</p>
 * <p>Description: 管道节点 — 用户消息频率限制，基于 Redis 固定窗口计数实现分布式限流，
 * 防止单个用户刷消息（跨节点生效，替代原单机内存限流）。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-07-24
 * @updateTime 2026-08-16
 */
@Component
public class RateLimitNode implements PipeNode<MessageContext> {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitNode.class);

    private static final String RATE_LIMIT_KEY_PREFIX = ":rateLimit:";

    /** 固定窗口计数 Lua 脚本：INCR 当前窗口计数，首次写入时设置过期时间，返回窗口内计数 */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) " +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return c",
            Long.class);

    private final MessageProducer messageProducer;
    private final ImServerProperties appConfig;
    private final StringRedisTemplate stringRedisTemplate;

    public RateLimitNode(MessageProducer messageProducer,
                         ImServerProperties appConfig,
                         StringRedisTemplate stringRedisTemplate) {
        this.messageProducer = messageProducer;
        this.appConfig = appConfig;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void process(MessageContext ctx, PipeChain<MessageContext> chain) {
        MessageContent msg = ctx.getMessageContent();
        String fromId = msg.getFromId();

        int rateLimit = appConfig.getMessageRateLimit() != null ? appConfig.getMessageRateLimit() : 20;
        long windowSeconds = 1L; // 固定窗口 1s
        // 窗口 key：appId + :rateLimit: + fromId + :秒级窗口号，天然按窗口滚动且可跨节点生效
        long window = System.currentTimeMillis() / (windowSeconds * 1000);
        String key = msg.getAppId() + RATE_LIMIT_KEY_PREFIX + fromId + ":" + window;

        Long count = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT,
                Collections.singletonList(key), String.valueOf(windowSeconds));
        if (count != null && count > rateLimit) {
            logger.warn("Rate limit exceeded for user={}, count={}, limit={}", fromId, count, rateLimit);
            ChatMessageAck ack = new ChatMessageAck(msg.getMessageId(), msg.getMessageSequence());
            Result<ChatMessageAck> result = Result.fail(MessageErrorCode.MESSAGE_RATE_LIMITED);
            result.setData(ack);
            messageProducer.sendToUser(fromId, MessageCommand.MSG_ACK, result, msg);
            ctx.interrupt(MessageErrorCode.MESSAGE_RATE_LIMITED);
            return;
        }

        chain.next(ctx);
    }
}
