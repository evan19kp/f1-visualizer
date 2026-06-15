package com.evanp.f1.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(boolean enabled, String sessionKey, boolean autoBootstrap) {}
