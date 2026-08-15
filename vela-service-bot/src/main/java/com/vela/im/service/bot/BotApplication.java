package com.vela.im.service.bot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.vela.im.service.bot", "com.vela.im.service.common", "com.vela.im.shared"})
@MapperScan("com.vela.im.service.bot.infrastructure.persistence.mapper")
public class BotApplication {
    public static void main(String[] args) {
        SpringApplication.run(BotApplication.class, args);
    }
}
