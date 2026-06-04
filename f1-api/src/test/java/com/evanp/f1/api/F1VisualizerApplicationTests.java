package com.evanp.f1.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Context load test — verifies the Spring ApplicationContext starts successfully.
 * Requires Docker for Postgres and Redis (Testcontainers integration comes in Sprint 7).
 *
 * <p>Run manually: ./mvnw test -pl f1-api (requires docker compose up -d first)
 */
@SpringBootTest
@ActiveProfiles("test")
class F1VisualizerApplicationTests {

    @Test
    void contextLoads() {
        // Passes if the Spring ApplicationContext starts without errors.
    }
}
