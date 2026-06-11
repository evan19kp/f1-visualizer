package com.evanp.f1.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@EnableConfigurationProperties(OpenAiProperties.class)
public class AiConfiguration {}
