package com.evanp.f1.api;

import com.evanp.f1.api.security.SecurityConfig;
import com.evanp.f1.api.websocket.RedisPositionBroadcastBridge;
import com.evanp.f1.api.websocket.WebSocketConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(
        basePackageClasses = {WebSocketConfig.class, SecurityConfig.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = RedisPositionBroadcastBridge.class))
@EnableScheduling
public class TestApplication {}
