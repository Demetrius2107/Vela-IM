package com.vela.im.service.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.vela.im.service.knowledge", "com.vela.im.service.common", "com.vela.im.shared"})
@MapperScan("com.vela.im.service.knowledge.infrastructure.persistence.mapper")
public class KnowledgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}
