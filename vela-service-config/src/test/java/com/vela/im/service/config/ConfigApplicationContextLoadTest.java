package com.vela.im.service.config;

import com.vela.im.service.config.domain.service.FeatureFlagService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * <p>Title: ConfigApplicationContextLoadTest</p>
 * <p>Description: Spring 上下文加载护栏测试，验证 ConfigApplication 的 Bean 组装无误。
 * 离线运行：HikariCP 跳过启动期连接（initialization-fail-timeout=-1）、RabbitMQ 监听容器
 * 不自动启动（auto-startup=false）；FeatureFlagService 的 @PostConstruct 启动时落库初始化
 * 默认开关并刷新缓存，离线环境以 @MockBean 隔离，防止破坏性改动导致启动期崩溃（如 30+ 启动问题回归）。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-13
 */
@SpringBootTest(
        classes = ConfigApplication.class,
        properties = {
                "spring.datasource.hikari.initialization-fail-timeout=-1",
                "spring.rabbitmq.listener.simple.auto-startup=false"
        })
class ConfigApplicationContextLoadTest {

    /**
     * 隔离 FeatureFlagService：其 @PostConstruct initDefaults() 启动时执行落库初始化，离线环境需 Mock
     */
    @MockBean
    private FeatureFlagService featureFlagService;

    /**
     * 验证 Spring 上下文可正常加载
     */
    @Test
    void contextLoads() {
    }
}
