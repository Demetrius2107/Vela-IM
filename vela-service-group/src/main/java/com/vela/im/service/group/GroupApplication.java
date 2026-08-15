package com.vela.im.service.group;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.vela.im.service.group", "com.vela.im.service.message", "com.vela.im.service.common", "com.vela.im.service.conversation", "com.vela.im.service.user", "com.vela.im.service.friendship", "com.vela.im.shared"})
@EnableFeignClients
@MapperScan({"com.vela.im.service.group.infrastructure.persistence.mapper",
        "com.vela.im.service.message.infrastructure.persistence.mapper",
        "com.vela.im.service.common.entity",
        "com.vela.im.service.conversation.infrastructure.persistence.mapper",
        "com.vela.im.service.user.infrastructure.persistence.mapper",
        "com.vela.im.service.friendship.infrastructure.persistence.mapper"})
public class GroupApplication {
    public static void main(String[] args) {
        SpringApplication.run(GroupApplication.class, args);
    }
}
