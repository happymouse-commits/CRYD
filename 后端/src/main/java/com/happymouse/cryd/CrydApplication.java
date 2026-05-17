package com.happymouse.cryd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CRYD - 基于大模型的个性化资源生成与学习多智能体系统
 * 中国软件杯第十五届 A3赛题
 */
@SpringBootApplication
public class CrydApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrydApplication.class, args);
        System.out.println("""

                ========================================
                  CRYD 多智能体学习系统启动成功！
                  讯飞星火 + Spring Boot
                ========================================
                """);
    }
}
