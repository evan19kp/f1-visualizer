package com.evanp.f1.api.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtSecretValidatorTest {

    private static final String VALID_SECRET = "a-secure-production-jwt-secret-32chars";

    @Test
    void acceptsValidSecret() {
        assertThatCode(() -> new JwtSecretValidator(new JwtProperties(VALID_SECRET, 86_400_000L)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptySecret() {
        assertThatThrownBy(() -> new JwtSecretValidator(new JwtProperties("", 86_400_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be set");
    }

    @Test
    void rejectsPlaceholderSecret() {
        assertThatThrownBy(() -> new JwtSecretValidator(
                        new JwtProperties("CHANGE_ME_IN_PRODUCTION_USE_MIN_256_BITS", 86_400_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtSecretValidator(new JwtProperties("too-short", 86_400_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }
}
