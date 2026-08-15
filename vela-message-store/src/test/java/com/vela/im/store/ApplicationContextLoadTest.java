package com.vela.im.store;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * <p>Title: ApplicationContextLoadTest</p>
 * <p>Description: Spring 上下文加载护栏测试，验证 message-store 的 Bean 组装无误。
 * 离线运行：HikariCP 跳过启动期连接（initialization-fail-timeout=-1）、RabbitMQ 监听容器
 * 不自动启动（auto-startup=false），用于防止破坏性改动导致启动期崩溃（如 30+ 启动问题回归）。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-13
 */
@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.hikari.initialization-fail-timeout=-1",
                "spring.rabbitmq.listener.simple.auto-startup=false"
        })
class ApplicationContextLoadTest {

    /**
     * 验证 Spring 上下文可正常加载
     */
    @Test
    void contextLoads() {
    }
}
