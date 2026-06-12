package com.evanp.f1.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class WebSocketOriginsValidator {

    public WebSocketOriginsValidator(@Value("${app.websocket.allowed-origins:}") String allowedOrigins) {
        if (!StringUtils.hasText(allowedOrigins)) {
            throw new IllegalStateException(
                    "WEBSOCKET_ALLOWED_ORIGINS must be set when the prod profile is active"
                            + " (comma-separated frontend origins, e.g. https://app.example.com)");
        }
    }
}
