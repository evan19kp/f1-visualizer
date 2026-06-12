package com.evanp.f1.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class AdminPasswordValidator {

    private static final String DEV_PASSWORD = "changeme";

    public AdminPasswordValidator(@Value("${spring.security.user.password:}") String password) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("ADMIN_PASSWORD must be set when the prod profile is active");
        }
        if (DEV_PASSWORD.equals(password)) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must not use the default dev value when the prod profile is active");
        }
    }
}
