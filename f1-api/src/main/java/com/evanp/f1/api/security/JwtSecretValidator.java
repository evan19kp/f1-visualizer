package com.evanp.f1.api.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class JwtSecretValidator {

    private static final String PLACEHOLDER_MARKER = "CHANGE_ME";
    private static final int MIN_SECRET_LENGTH = 32;

    public JwtSecretValidator(JwtProperties properties) {
        String secret = properties.secret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT_SECRET must be set when the prod profile is active");
        }
        if (secret.contains(PLACEHOLDER_MARKER)) {
            throw new IllegalStateException(
                    "JWT_SECRET must not use the default placeholder when the prod profile is active");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_LENGTH + " characters in production");
        }
    }
}
