package com.vela.im.tcp.interfaces.utils;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Title: ChannelWriteUtil</p>
 * <p>Description: 通道写入工具 — 背压感知写入。Netty 写缓冲区不可写（高水位）时
 * 降级跳过本次推送并记录告警，避免下行堆积导致 OOM/延迟放大；通道失活时同样跳过。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-16
 */
public final class ChannelWriteUtil {

    private static final Logger log = LoggerFactory.getLogger(ChannelWriteUtil.class);

    private ChannelWriteUtil() {
    }

    /**
     * 背压感知写入：通道活跃且写缓冲区可写时才推送。
     *
     * @param channel 目标通道
     * @param msg     待推送消息
     * @param desc    推送场景描述（用于告警日志定位）
     * @return true-已写入，false-背压/失活降级跳过
     */
    public static boolean safeWrite(Channel channel, Object msg, String desc) {
        if (channel == null || !channel.isActive()) {
            log.warn("ChannelWrite skip: channel inactive, desc={}", desc);
            return false;
        }
        if (!channel.isWritable()) {
            log.warn("ChannelWrite skip: backpressure (not writable), desc={}", desc);
            return false;
        }
        channel.writeAndFlush(msg);
        return true;
    }
}
