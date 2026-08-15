package com.vela.im.service.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.vela.im.service.config", "com.vela.im.service.common", "com.vela.im.shared"})
@MapperScan("com.vela.im.service.config.infrastructure.persistence.mapper")
public class ConfigApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigApplication.class, args);
    }
}
