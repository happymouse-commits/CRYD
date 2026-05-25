package com.happymouse.cryd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CrydApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrydApplication.class, args);
        System.out.println("""

                ========================================
                  CRYD 多智能体学习系统启动成功！
                  DeepSeek V4 Pro + Spring Boot
                ========================================
                """);
    }
}
