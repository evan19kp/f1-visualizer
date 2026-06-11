package com.evanp.f1.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final JwtProperties PROPERTIES =
            new JwtProperties("test-jwt-secret-at-least-32-characters", 3_600_000L);

    private final JwtService jwtService = new JwtService(PROPERTIES);

    @Test
    void generateAndValidate_roundTripsSubject() {
        String token = jwtService.generateToken("admin");

        assertThat(jwtService.validateAndGetSubject(token)).contains("admin");
    }

    @Test
    void validateAndGetSubject_rejectsInvalidToken() {
        assertThat(jwtService.validateAndGetSubject("not-a-jwt")).isEmpty();
    }
}
