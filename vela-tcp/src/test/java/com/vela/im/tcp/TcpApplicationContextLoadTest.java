package com.vela.im.tcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * <p>Title: TcpApplicationContextLoadTest</p>
 * <p>Description: Spring 上下文加载护栏测试，验证 TcpApplication 的 Bean 组装无误。
 * 通过 Mock 掉 TcpServerRunner 隔离 Redis/MQ/ZK 外部依赖，使测试可离线运行，
 * 用于防止破坏性改动导致启动期崩溃（如 30+ 启动问题回归）。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-11
 */
@SpringBootTest(classes = TcpApplication.class)
class TcpApplicationContextLoadTest {

    /**
     * 隔离 TcpServerRunner：上下文加载时不会真正初始化 Redis/MQ/Netty/ZK
     */
    @MockBean
    private TcpServerRunner tcpServerRunner;

    /**
     * 验证 Spring 上下文可正常加载
     */
    @Test
    void contextLoads() {
    }
}
