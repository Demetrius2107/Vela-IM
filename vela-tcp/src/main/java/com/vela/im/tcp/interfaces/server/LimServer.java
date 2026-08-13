package com.vela.im.tcp.interfaces.server;

import com.vela.im.codec.protocol.MessageDecoder;
import com.vela.im.codec.protocol.MessageEncoder;
import com.vela.im.codec.config.BootStrapConfig;
import com.vela.im.tcp.interfaces.handler.HeartBeatHandler;
import com.vela.im.tcp.interfaces.handler.NettyServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * <p>Title: LimServer</p>
 * <p>Description: TCP 网关服务器，基于 Netty 构建，处理客户端 TCP 长连接消息</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-06
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
@Slf4j
public class LimServer {

    /** TCP 服务配置 */
    private final BootStrapConfig.ServerConfig config;

    /** 主线程组（Boss） */
    private final EventLoopGroup mainGroup;

    /** 工作线程组（Worker） */
    private final EventLoopGroup subGroup;

    /** Netty 服务启动器 */
    private final ServerBootstrap server;

    /**
     * 构造 TCP 网关服务器，配置 Netty 线程模型与 Channel 管道
     *
     * @param config TCP 服务配置
     */
    public LimServer(BootStrapConfig.ServerConfig config) {
        this.config = config;
        mainGroup = new NioEventLoopGroup(config.getBossThreadSize());
        subGroup = new NioEventLoopGroup(config.getWorkThreadSize());
        server = new ServerBootstrap();
        server.group(mainGroup, subGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 10240)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // 空闲检测处理器：读+写均空闲超过 heartBeatTime（秒，至少 1s）时触发 ALL_IDLE 事件，
                        // 供 HeartBeatHandler 做心跳超时判断；必须位于 HeartBeatHandler 之前
                        ch.pipeline().addLast(new IdleStateHandler(0, 0,
                                Math.max(1, (int) (config.getHeartBeatTime() / 1000)), TimeUnit.SECONDS));
                        ch.pipeline().addLast(new MessageDecoder());
                        ch.pipeline().addLast(new MessageEncoder());
                        ch.pipeline().addLast(new HeartBeatHandler(config.getHeartBeatTime()));
                        ch.pipeline().addLast(new NettyServerHandler(config.getBrokerId(), config.getLogicUrl()));
                    }
                });
    }

    /**
     * 启动 TCP 服务器，绑定端口
     */
    public void start() {
        this.server.bind(this.config.getTcpPort());
    }

    /**
     * 关闭 TCP 服务器，释放 Boss/Worker 线程组
     */
    public void close() {
        mainGroup.shutdownGracefully();
        subGroup.shutdownGracefully();
    }
}