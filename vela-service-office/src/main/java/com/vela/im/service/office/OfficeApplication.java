package com.vela.im.service.office;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.vela.im.service.office", "com.vela.im.service.common", "com.vela.im.shared"})
@MapperScan("com.vela.im.service.office.infrastructure.persistence.mapper")
public class OfficeApplication {
    public static void main(String[] args) {
        SpringApplication.run(OfficeApplication.class, args);
    }
}
