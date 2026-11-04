package com.linksprite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 连接精灵链后端服务启动类
 */
@SpringBootApplication
@EnableConfigurationProperties
public class LinkSpriteApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkSpriteApplication.class, args);
        System.out.println("\n============================================");
        System.out.println("  连接精灵链后端服务启动成功");
        System.out.println("  访问: http://localhost:8080/api");
        System.out.println("  健康检查: http://localhost:8080/api/health");
        System.out.println("============================================\n");
    }
}
