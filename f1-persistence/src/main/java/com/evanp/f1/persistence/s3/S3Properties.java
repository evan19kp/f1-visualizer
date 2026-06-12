package com.evanp.f1.persistence.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(String bucket, String region, String endpoint) {}
