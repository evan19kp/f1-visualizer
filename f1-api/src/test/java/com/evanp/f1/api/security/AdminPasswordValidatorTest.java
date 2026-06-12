package com.evanp.f1.api.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AdminPasswordValidatorTest {

    @Test
    void acceptsStrongPassword() {
        assertThatCode(() -> new AdminPasswordValidator("s3cure-pr0d-p@ssw0rd"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyPassword() {
        assertThatThrownBy(() -> new AdminPasswordValidator(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD must be set");
    }

    @Test
    void rejectsDevDefaultPassword() {
        assertThatThrownBy(() -> new AdminPasswordValidator("changeme"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default dev value");
    }
}
