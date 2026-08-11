package com.vela.im.tcp.interfaces.handler;

import com.vela.im.shared.constants.ImConstants;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Title: HeartBeatHandler</p>
 * <p>Description: Netty 心跳检测处理器，监听读/写/全空闲事件，超时后执行退后台逻辑</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
@Slf4j
public class HeartBeatHandler extends ChannelInboundHandlerAdapter {

    /** 心跳检测超时时间（毫秒） */
    private final Long heartBeatTime;

    /**
     * 构造心跳处理器
     *
     * @param heartBeatTime 心跳超时时间（毫秒）
     */
    public HeartBeatHandler(Long heartBeatTime) {
        this.heartBeatTime = heartBeatTime;
    }

    /**
     * 用户事件触发器，处理 IdleStateEvent 空闲状态事件
     *
     * @param ctx 通道处理器上下文
     * @param evt 事件对象
     * @throws Exception 处理异常
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.info("读空闲");
            } else if (event.state() == IdleState.WRITER_IDLE) {
                log.info("进入写空闲");
            } else if (event.state() == IdleState.ALL_IDLE) {
                Long lastReadTime = (Long) ctx.channel()
                        .attr(AttributeKey.valueOf(ImConstants.READ_TIME)).get();

                long now = System.currentTimeMillis();

                // 超过心跳超时未收到客户端 PING（或从未 PING 过），视为死连接执行退后台：
                // 关闭连接后由 channelInactive -> offlineUserSession 完成 Session/Redis 清理与下线通知
                if (lastReadTime == null || now - lastReadTime > heartBeatTime) {
                    log.warn("心跳超时，退后台并关闭连接: remote={}, lastReadTime={}",
                            ctx.channel().remoteAddress(), lastReadTime);
                    ctx.close();
                }
            }
        }
    }
}