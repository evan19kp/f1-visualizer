package com.evanp.f1.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.SessionBounds;
import com.evanp.f1.persistence.support.AbstractContainersIT;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisPositionStoreIT extends AbstractContainersIT {

    private static final long SESSION_KEY = 9161L;

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisPositionStore store;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        ObjectMapper objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        store = new RedisPositionStore(redisTemplate, objectMapper);
        flushRedisDb(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        flushRedisDb(redisTemplate);
        destroyRedisResources(connectionFactory);
    }

    @Test
    void savePositions_roundTripsThroughRealRedis() {
        Instant timestamp = Instant.parse("2024-03-02T15:00:00Z");
        NormalizedPosition position =
                new NormalizedPosition(44, SESSION_KEY, timestamp, 1.2, 3.4, 5.6);

        store.savePositions(SESSION_KEY, List.of(position));

        assertThat(store.getLatest(SESSION_KEY, 44)).contains(position);
        assertThat(store.getAllPositions(SESSION_KEY)).containsExactly(position);
    }

    @Test
    void saveBounds_roundTripsThroughRealRedis() {
        SessionBounds bounds = SessionBounds.empty().expand(1.0, 2.0, 3.0);

        store.saveBounds(SESSION_KEY, bounds);

        assertThat(store.getBounds(SESSION_KEY)).contains(bounds);
    }

    @Test
    void savePollCursor_roundTripsIso8601String() {
        Instant cursor = Instant.parse("2024-03-02T15:00:02Z");

        store.savePollCursor(SESSION_KEY, cursor);

        assertThat(store.getPollCursor(SESSION_KEY)).contains(cursor);
    }

    @Test
    void appendHistory_andGetFrameAt_roundTrip() {
        Instant t1 = Instant.parse("2024-03-02T15:00:00Z");
        Instant t2 = Instant.parse("2024-03-02T15:00:05Z");
        NormalizedPosition first = new NormalizedPosition(1, SESSION_KEY, t1, 0.1, 0.2, 0.3);
        NormalizedPosition second = new NormalizedPosition(44, SESSION_KEY, t2, 0.4, 0.5, 0.6);

        store.appendHistory(SESSION_KEY, List.of(first));
        store.appendHistory(SESSION_KEY, List.of(second));

        assertThat(store.hasHistory(SESSION_KEY)).isTrue();
        assertThat(store.getFrameAt(SESSION_KEY, t2)).containsExactly(second);
        assertThat(store.getHistoryTimeRange(SESSION_KEY))
                .contains(new com.evanp.f1.core.position.SessionTimeRange(t1, t2));
    }
}
