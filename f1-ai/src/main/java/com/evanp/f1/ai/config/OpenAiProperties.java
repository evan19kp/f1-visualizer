package com.evanp.f1.ai.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.openai")
public record OpenAiProperties(String apiKey, @NotBlank String model, @Min(1) int rateLimitSeconds) {}
