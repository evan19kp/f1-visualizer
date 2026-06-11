package com.evanp.f1.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openai")
public record OpenAiProperties(String apiKey, String model, int rateLimitSeconds) {}
