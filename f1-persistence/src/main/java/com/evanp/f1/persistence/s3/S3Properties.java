package com.evanp.f1.persistence.s3;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(@NotBlank String bucket, @NotBlank String region, String endpoint) {}
