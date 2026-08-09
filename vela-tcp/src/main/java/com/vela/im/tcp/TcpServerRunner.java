package com.vela.im.tcp;

import com.vela.im.codec.config.BootStrapConfig;
import com.vela.im.tcp.infrastructure.redis.RedisManager;
import com.vela.im.tcp.infrastructure.register.RegistryZK;
import com.vela.im.tcp.infrastructure.register.Zkit;
import com.vela.im.tcp.infrastructure.utils.MqFactory;
import com.vela.im.tcp.interfaces.reciver.MessageReceiver;
import com.vela.im.tcp.interfaces.server.LimServer;
import com.vela.im.tcp.interfaces.server.LimWebSocketServer;
import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.InetAddress;

/**
 * <p>Title: TcpServerRunner</p>
 * <p>Description: Spring Boot 启动后初始化 TCP/WebSocket 网关、Redis、MQ、ZooKeeper。
 * 替代原 Starter 类的手动生命周期管理。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-09
 */
@Component
public class TcpServerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TcpServerRunner.class);

    @Override
    public void run(String... args) {
        log.info("Loading configuration from classpath:config.yml");

        BootStrapConfig bootStrapConfig;
        try (InputStream inputStream = new ClassPathResource("config.yml").getInputStream()) {
            Yaml yaml = new Yaml();
            bootStrapConfig = yaml.loadAs(inputStream, BootStrapConfig.class);
        } catch (Exception e) {
            log.error("Failed to load config.yml from classpath", e);
            System.exit(500);
            return;
        }

        BootStrapConfig.ServerConfig tcpConfig = bootStrapConfig.getServerConfig();
        if (tcpConfig == null) {
            log.error("Config missing: 'serverConfig' section is required");
            System.exit(500);
            return;
        }

        // 启动 TCP 和 WebSocket 网关
        log.info("Starting TCP server on port: {}", tcpConfig.getTcpPort());
        new LimServer(tcpConfig).start();
        new LimWebSocketServer(tcpConfig).start();

        // 初始化 Redis
        log.info("Initializing Redis connection");
        RedisManager.init(bootStrapConfig);

        // 初始化 RabbitMQ
        if (tcpConfig.getRabbitmqConfig() != null) {
            log.info("Initializing RabbitMQ connection");
            MqFactory.init(tcpConfig.getRabbitmqConfig());
        }

        // 初始化消息接收器
        log.info("Initializing message receiver for brokerId: {}", tcpConfig.getBrokerId());
        MessageReceiver.init(tcpConfig.getBrokerId() + "");

        // ZooKeeper 注册
        log.info("Registering to ZooKeeper");
        registerZK(bootStrapConfig);

        log.info("TcpServerRunner completed — TCP gateway is ready");
    }

    private void registerZK(BootStrapConfig config) {
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            ZkClient zkClient = new ZkClient(
                    config.getServerConfig().getZookeeperConfig().getZkAddr(),
                    config.getServerConfig().getZookeeperConfig().getZkConnectTimeOut());
            Zkit zkit = new Zkit(zkClient);
            RegistryZK registryZK = new RegistryZK(zkit, hostAddress, config.getServerConfig());
            Thread thread = new Thread(registryZK, "zk-registry");
            thread.setDaemon(true);
            thread.start();
            log.info("ZooKeeper registration started, addr: {}", hostAddress);
        } catch (Exception e) {
            log.error("Failed to get local host address for ZK registration", e);
            System.exit(500);
        }
    }
}
