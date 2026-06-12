package com.evanp.f1.api.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class CorsOriginsValidator {

    public CorsOriginsValidator(@Value("${app.cors.allowed-origins:}") String corsOrigins) {
        if (!StringUtils.hasText(corsOrigins)) {
            return;
        }
        List<String> origins = parseOrigins(corsOrigins);
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "APP_CORS_ORIGINS must list at least one origin when set in production");
        }
        for (String origin : origins) {
            if ("*".equals(origin)) {
                throw new IllegalStateException(
                        "APP_CORS_ORIGINS cannot include '*' when credentials are enabled");
            }
            if (!origin.startsWith("http://") && !origin.startsWith("https://")) {
                throw new IllegalStateException(
                        "APP_CORS_ORIGINS entries must start with http:// or https://: " + origin);
            }
        }
    }

    static List<String> parseOrigins(String corsOrigins) {
        return Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
