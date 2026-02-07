package com.jjy.wgcbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@MapperScan("com.jjy.wgcbackend.mapper")
@EnableCaching
public class WgcBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WgcBackendApplication.class, args);
    }

}
