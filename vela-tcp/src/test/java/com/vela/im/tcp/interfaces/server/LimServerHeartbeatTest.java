package com.vela.im.tcp.interfaces.server;

import com.vela.im.codec.config.BootStrapConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <p>Title: LimServerHeartbeatTest</p>
 * <p>Description: TCP 心跳机制端到端测试，验证 LimServer pipeline 中
 * IdleStateHandler 产生 ALL_IDLE 事件后，HeartBeatHandler 能按心跳超时断开死连接。
 * 仅发送 PING 报文，不触发登录链路，不依赖 Redis/MQ 等中间件。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-11
 */
class LimServerHeartbeatTest {

    /** 心跳超时时间，配置为 3 秒以加速测试 */
    private static final long HEARTBEAT_TIMEOUT_MS = 3000L;

    private static final String IMEI = "test-imei-001";

    private LimServer server;
    private EventLoopGroup clientGroup;
    private int port;

    /**
     * 每个用例前启动 TCP 服务并准备客户端线程组
     *
     * @throws Exception 服务启动或端口探测失败
     */
    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        BootStrapConfig.ServerConfig config = new BootStrapConfig.ServerConfig();
        config.setTcpPort(port);
        config.setHeartBeatTime(HEARTBEAT_TIMEOUT_MS);
        config.setBossThreadSize(1);
        config.setWorkThreadSize(2);
        config.setBrokerId(1);
        config.setLogicUrl("http://127.0.0.1:1");
        server = new LimServer(config);
        server.start();
        clientGroup = new NioEventLoopGroup(1);
        awaitServerReady();
    }

    /**
     * 每个用例后关闭服务端线程组与客户端线程组
     */
    @AfterEach
    void tearDown() {
        clientGroup.shutdownGracefully();
        server.close();
    }

    /**
     * 连接建立后不发任何数据，应在心跳超时后被服务端断开
     *
     * @throws Exception 连接或等待异常
     */
    @Test
    void idleConnectionClosedOnTimeout() throws Exception {
        Channel channel = connect();
        try {
            // 等待超过心跳超时（含 IdleStateHandler tick 余量）
            channel.closeFuture().await(HEARTBEAT_TIMEOUT_MS + 4000, TimeUnit.MILLISECONDS);
            assertFalse(channel.isActive(), "空闲连接应在心跳超时后被服务端关闭");
        } finally {
            channel.close();
        }
    }

    /**
     * 持续发送 PING 的连接应保持活跃，不会被心跳逻辑断开
     *
     * @throws Exception 连接或发送异常
     */
    @Test
    void activePingConnectionStaysAlive() throws Exception {
        Channel channel = connect();
        try {
            AtomicBoolean closed = new AtomicBoolean(false);
            channel.closeFuture().addListener(future -> closed.set(true));

            // 每 500ms 发一次 PING，持续 5 秒（大于 3 秒心跳超时）
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                assertTrue(channel.isActive(), "持续 PING 的连接不应被断开");
                channel.writeAndFlush(buildPing());
                Thread.sleep(500);
            }
            assertFalse(closed.get(), "持续 PING 的连接不应触发心跳断开");
        } finally {
            channel.close();
        }
    }

    /**
     * 保持活跃后停止发送 PING，应在心跳超时后被服务端断开
     *
     * @throws Exception 连接或等待异常
     */
    @Test
    void pingStoppedConnectionClosedOnTimeout() throws Exception {
        Channel channel = connect();
        try {
            // 先发送几次 PING 保持活跃
            for (int i = 0; i < 2; i++) {
                channel.writeAndFlush(buildPing());
                Thread.sleep(500);
            }
            assertTrue(channel.isActive(), "发送 PING 后连接应处于活跃状态");

            // 停止发送，等待超过心跳超时
            channel.closeFuture().await(HEARTBEAT_TIMEOUT_MS + 4000, TimeUnit.MILLISECONDS);
            assertFalse(channel.isActive(), "停发 PING 后连接应在心跳超时后被服务端关闭");
        } finally {
            channel.close();
        }
    }

    /**
     * 建立到服务端的客户端连接
     *
     * @return 已连接的 Channel
     * @throws InterruptedException 连接被中断
     */
    private Channel connect() throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(clientGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 客户端仅需观察服务端关闭事件，无需解码
                    }
                });
        return bootstrap.connect("127.0.0.1", port).sync().channel();
    }

    /**
     * 构造 PING 报文：28 字节定长包头 + imei + body（与客户端脚本 heartBeat.py 一致）
     *
     * @return PING 报文 ByteBuf
     */
    private static ByteBuf buildPing() {
        byte[] imei = IMEI.getBytes(StandardCharsets.UTF_8);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(0x270f);              // command = PING
        buf.writeInt(1);                   // version
        buf.writeInt(4);                   // clientType
        buf.writeInt(0x0);                 // messageType = JSON
        buf.writeInt(10000);               // appId
        buf.writeInt(imei.length);         // imeiLength
        buf.writeInt(body.length);         // bodyLength
        buf.writeBytes(imei);
        buf.writeBytes(body);
        return buf;
    }

    /**
     * 等待服务端完成端口绑定
     *
     * @throws Exception 等待超时
     */
    private void awaitServerReady() throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return;
            } catch (IOException ignored) {
                Thread.sleep(100);
            }
        }
        fail("TCP server did not bind port " + port + " within 3s");
    }

    /**
     * 探测一个空闲 TCP 端口
     *
     * @return 空闲端口号
     * @throws IOException 端口探测失败
     */
    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
