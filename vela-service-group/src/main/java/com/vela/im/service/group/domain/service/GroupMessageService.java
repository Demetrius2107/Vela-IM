package com.vela.im.service.group.domain.service;

import com.vela.im.codec.pack.message.ChatMessageAck;
import com.vela.im.service.common.pipeline.MessageContext;
import com.vela.im.service.common.pipeline.PipeChain;
import com.vela.im.service.message.domain.pipeline.node.DedupNode;
import com.vela.im.service.group.domain.pipeline.node.GroupBroadcastNode;
import com.vela.im.service.group.domain.pipeline.node.GroupValidateNode;
import com.vela.im.service.message.domain.pipeline.node.RateLimitNode;
import com.vela.im.service.group.application.dto.req.SendGroupMessageRequest;
import com.vela.im.service.message.application.dto.resp.SendMessageResp;
import com.vela.im.service.message.domain.service.MessageStoreService;
import com.vela.im.service.common.infrastructure.seq.RedisSeq;
import com.vela.im.service.common.utils.MessageProducer;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.config.ImServerProperties;
import com.vela.im.shared.types.enums.command.GroupEventCommand;
import com.vela.im.shared.types.message.GroupChatMessageContent;
import com.vela.im.shared.types.message.OfflineMessageContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>Title: GroupMessageService</p>
 * <p>Description: 群聊消息处理服务，采用管道模式编排处理流程：
 * GroupValidateNode → RateLimitNode → DedupNode → GroupBroadcastNode。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2025-03-06
 * @updateTime 2026-07-24
 *
 * Copyright © 2026 wanqiu All rights reserved
 */
@Service
public class GroupMessageService {

    private static final Logger logger = LoggerFactory.getLogger(GroupMessageService.class);

    private final PipeChain<MessageContext> syncPipeline;
    private final GroupBroadcastNode groupBroadcastNode;

    private final MessageProducer messageProducer;
    private final ImGroupMemberService imGroupMemberService;
    private final MessageStoreService messageStoreService;
    private final RedisSeq redisSeq;
    private final ImServerProperties appConfig;

    private final ThreadPoolExecutor threadPoolExecutor;

    public GroupMessageService(MessageProducer messageProducer,
                               ImGroupMemberService imGroupMemberService,
                               MessageStoreService messageStoreService,
                               RedisSeq redisSeq,
                               ImServerProperties appConfig,
                               GroupValidateNode groupValidateNode,
                               RateLimitNode rateLimitNode,
                               DedupNode dedupNode,
                               GroupBroadcastNode groupBroadcastNode) {
        this.messageProducer = messageProducer;
        this.imGroupMemberService = imGroupMemberService;
        this.messageStoreService = messageStoreService;
        this.redisSeq = redisSeq;
        this.appConfig = appConfig;
        this.groupBroadcastNode = groupBroadcastNode;

        // 装配同步管道：群聊校验 → 限流 → 去重
        this.syncPipeline = new PipeChain<>(Arrays.asList(groupValidateNode, rateLimitNode, dedupNode));

        // 异步线程池
        final AtomicInteger num = new AtomicInteger(0);
        this.threadPoolExecutor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000), r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("message-group-thread-" + num.getAndIncrement());
            return thread;
        });
    }

    /**
     * 处理群聊消息主流程。
     * <p>同步管道（校验+限流+去重）→ 通过后异步广播给群成员。</p>
     *
     * @param messageContent 群聊消息内容
     */
    public void process(GroupChatMessageContent messageContent) {
        logger.info("Processing group message: msgId={}, groupId={}",
                messageContent.getMessageId(), messageContent.getGroupId());

        // 同步管道：校验 → 限流 → 去重
        MessageContext ctx = new MessageContext(messageContent);
        syncPipeline.process(ctx);

        if (ctx.isInterrupted()) {
            return;
        }

        // 通过同步管道，进入异步广播
        threadPoolExecutor.execute(() -> {
            MessageContext asyncCtx = new MessageContext(messageContent);
            PipeChain<MessageContext> asyncPipe = new PipeChain<>(Arrays.asList(groupBroadcastNode));
            asyncPipe.process(asyncCtx);
        });
    }

    /**
     * 同步发送群聊消息（REST API 入口）。
     */
    public SendMessageResp send(SendGroupMessageRequest req) {
        SendMessageResp resp = new SendMessageResp();
        GroupChatMessageContent message = new GroupChatMessageContent();
        BeanUtils.copyProperties(req, message);
        messageStoreService.storeGroupMessage(message);
        resp.setMessageKey(message.getMessageKey());
        resp.setMessageTime(System.currentTimeMillis());
        messageProducer.sendToUserExceptClient(message.getFromId(), GroupEventCommand.MSG_GROUP, message, message);
        // send() 路径无成员列表时不分发
        return resp;
    }
}
