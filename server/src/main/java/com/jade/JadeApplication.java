package com.jade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 玉器门店系统启动类。业务分层 Controller / Service / Mapper，业务规则只写在 Service。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.jade.mapper")
public class JadeApplication {
    public static void main(String[] args) {
        SpringApplication.run(JadeApplication.class, args);
    }
}
