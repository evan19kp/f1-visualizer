package com.evanp.f1.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.evanp.f1.ai.config.OpenAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ProdProfileStartupTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProdValidatorTestConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "spring.security.user.password=s3cure-pr0d-p@ssw0rd",
                    "app.websocket.allowed-origins=https://app.example.com",
                    "app.ai.enabled=false",
                    "app.openai.api-key=",
                    "app.openai.model=gpt-4o-mini",
                    "app.openai.rate-limit-seconds=30");

    @Test
    void prodContextStartsWithValidSecrets() {
        runner.withPropertyValues("app.jwt.secret=a-secure-production-jwt-secret-32chars")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void prodContextFailsWithPlaceholderJwtSecret() {
        runner.withPropertyValues("app.jwt.secret=CHANGE_ME_IN_PRODUCTION_USE_MIN_256_BITS")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodContextFailsWithDevAdminPassword() {
        runner.withPropertyValues(
                        "app.jwt.secret=a-secure-production-jwt-secret-32chars",
                        "spring.security.user.password=changeme")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodContextFailsWithoutWebSocketOrigins() {
        runner.withPropertyValues(
                        "app.jwt.secret=a-secure-production-jwt-secret-32chars",
                        "app.websocket.allowed-origins=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodContextFailsWhenAiEnabledWithoutOpenAiKey() {
        runner.withPropertyValues(
                        "app.jwt.secret=a-secure-production-jwt-secret-32chars",
                        "app.ai.enabled=true",
                        "app.openai.api-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodContextFailsWithWildcardCorsOrigins() {
        runner.withPropertyValues(
                        "app.jwt.secret=a-secure-production-jwt-secret-32chars",
                        "app.cors.allowed-origins=*")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties({JwtProperties.class, OpenAiProperties.class})
    @Import({
        JwtSecretValidator.class,
        AdminPasswordValidator.class,
        WebSocketOriginsValidator.class,
        OpenAiKeyValidator.class,
        CorsOriginsValidator.class
    })
    static class ProdValidatorTestConfiguration {}
}
