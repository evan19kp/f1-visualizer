package com.evanp.f1.api.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class JwtSecretValidator {

    public JwtSecretValidator(JwtProperties properties) {
        if (!StringUtils.hasText(properties.secret())) {
            throw new IllegalStateException("app.jwt.secret must be set when the prod profile is active");
        }
    }
}
