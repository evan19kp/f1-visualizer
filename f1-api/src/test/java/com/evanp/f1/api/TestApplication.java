package com.evanp.f1.api;

import com.evanp.f1.api.security.JwtProperties;
import com.evanp.f1.api.websocket.RedisPositionBroadcastBridge;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(
        basePackages = {
            "com.evanp.f1.api.security",
            "com.evanp.f1.api.auth",
            "com.evanp.f1.api.websocket"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = RedisPositionBroadcastBridge.class))
@EnableConfigurationProperties(JwtProperties.class)
@EnableScheduling
public class TestApplication {}
