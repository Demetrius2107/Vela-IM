package com.vela.im.tcp.interfaces.server;

import com.vela.im.codec.protocol.WebSocketMessageDecoder;
import com.vela.im.codec.protocol.WebSocketMessageEncoder;
import com.vela.im.codec.config.BootStrapConfig;
import com.vela.im.tcp.interfaces.handler.NettyServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Title: LimWebSocketServer</p>
 * <p>Description: WebSocket 网关服务器，基于 Netty 构建，处理客户端 WebSocket 长连接消息</p>
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
public class LimWebSocketServer {

    /** WebSocket 服务配置 */
    private final BootStrapConfig.ServerConfig config;

    /** 主线程组（Boss） */
    private final EventLoopGroup mainGroup;

    /** 工作线程组（Worker） */
    private final EventLoopGroup subGroup;

    /** Netty 服务启动器 */
    private final ServerBootstrap server;

    /**
     * 构造 WebSocket 网关服务器，配置 HTTP/WebSocket 编解码器与管道
     *
     * @param config TCP 服务配置（含 WebSocket 端口）
     */
    public LimWebSocketServer(BootStrapConfig.ServerConfig config) {
        this.config = config;
        mainGroup = new NioEventLoopGroup();
        subGroup = new NioEventLoopGroup();
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
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("http-codec", new HttpServerCodec());
                        pipeline.addLast("http-chunked", new ChunkedWriteHandler());
                        pipeline.addLast("aggregator", new HttpObjectAggregator(65535));
                        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
                        pipeline.addLast(new WebSocketMessageDecoder());
                        pipeline.addLast(new WebSocketMessageEncoder());
                        pipeline.addLast(new NettyServerHandler(config.getBrokerId(), config.getLogicUrl()));
                    }
                });
    }

    /**
     * 启动 WebSocket 服务器，绑定端口
     */
    public void start() {
        this.server.bind(this.config.getWebSocketPort());
    }
}