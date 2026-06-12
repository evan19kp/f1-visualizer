package com.evanp.f1.api.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CorsOriginsValidatorTest {

    @Test
    void skipsValidationWhenUnset() {
        assertThatCode(() -> new CorsOriginsValidator("")).doesNotThrowAnyException();
    }

    @Test
    void acceptsValidOrigins() {
        assertThatCode(() -> new CorsOriginsValidator("https://app.example.com, http://localhost:8080"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWhitespaceOnlyValue() {
        assertThatThrownBy(() -> new CorsOriginsValidator("  ,  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one origin");
    }

    @Test
    void rejectsWildcard() {
        assertThatThrownBy(() -> new CorsOriginsValidator("*"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot include '*'");
    }

    @Test
    void rejectsInvalidScheme() {
        assertThatThrownBy(() -> new CorsOriginsValidator("ftp://app.example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("http:// or https://");
    }
}
