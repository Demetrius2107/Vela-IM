package com.vela.im.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Title: Starter</p>
 * <p>Description: 向后兼容启动入口，内部委托至 Spring Boot TcpApplication。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-03
 * @updateTime 2026-08-09
 */
public class Starter {

    private static final Logger log = LoggerFactory.getLogger(Starter.class);

    public static void main(String[] args) {
        log.info("Starter is deprecated. Delegating to Spring Boot TcpApplication...");
        TcpApplication.main(args);
    }
}