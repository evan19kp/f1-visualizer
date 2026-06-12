package com.evanp.f1.api.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebSocketOriginsValidatorTest {

    @Test
    void acceptsExplicitOrigins() {
        assertThatCode(() -> new WebSocketOriginsValidator("https://app.example.com,https://www.example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrigins() {
        assertThatThrownBy(() -> new WebSocketOriginsValidator(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBSOCKET_ALLOWED_ORIGINS must be set");
    }
}
