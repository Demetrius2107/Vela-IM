package com.vela.im.tcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * <p>Title: TcpApplication</p>
 * <p>Description: TCP 网关 Spring Boot 启动入口。
 * 排除 DataSourceAutoConfiguration（网关不需要数据库）。
 * 启动后由 TcpServerRunner 按原有逻辑初始化 Netty/Redis/MQ/ZK 组件。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-09
 */
@SpringBootApplication(
    scanBasePackages = {"com.vela.im.tcp", "com.vela.im.codec"},
    exclude = DataSourceAutoConfiguration.class
)
public class TcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(TcpApplication.class, args);
    }
}
