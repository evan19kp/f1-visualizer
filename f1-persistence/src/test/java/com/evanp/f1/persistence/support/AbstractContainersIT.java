package com.evanp.f1.persistence.support;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Shared Redis and Postgres containers for persistence integration tests. */
@Testcontainers
public abstract class AbstractContainersIT {

    protected static final String POSTGRES_IMAGE = "postgres:16";
    protected static final String REDIS_IMAGE = "redis:7-alpine";

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("f1")
                    .withUsername("f1user")
                    .withPassword("f1pass");

    @Container
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));
    }

    protected static void flushRedisDb(StringRedisTemplate redisTemplate) {
        var connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return;
        }
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    protected static void destroyRedisResources(LettuceConnectionFactory connectionFactory) {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }
}
