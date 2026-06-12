package com.evanp.f1.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.evanp.f1.ai.insight.InsightStore;
import com.evanp.f1.ai.insight.RaceInsight;
import com.evanp.f1.core.event.RaceEventType;
import com.evanp.f1.persistence.support.AbstractContainersIT;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisInsightStoreIT extends AbstractContainersIT {

    private static final long SESSION_KEY = 9161L;

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisInsightStore store;

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
        store = new RedisInsightStore(redisTemplate, objectMapper);
        flushRedisDb(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        flushRedisDb(redisTemplate);
        destroyRedisResources(connectionFactory);
    }

    @Test
    void saveAndGetRecent_roundTripsThroughRealRedis() {
        RaceInsight insight =
                new RaceInsight(SESSION_KEY, Instant.parse("2024-03-02T15:00:00Z"), RaceEventType.OVERTAKE, "P3 passes P4");

        store.save(SESSION_KEY, insight);

        assertThat(store.getRecent(SESSION_KEY, 10)).containsExactly(insight);
    }

    @Test
    void save_capsListLengthAtMaxPerSession() {
        List<RaceInsight> saved = new ArrayList<>(InsightStore.MAX_INSIGHTS_PER_SESSION + 5);
        for (int i = 0; i < InsightStore.MAX_INSIGHTS_PER_SESSION + 5; i++) {
            RaceInsight insight = new RaceInsight(
                    SESSION_KEY,
                    Instant.parse("2024-03-02T15:00:00Z").plusSeconds(i),
                    RaceEventType.PIT_WINDOW,
                    "Insight " + i);
            store.save(SESSION_KEY, insight);
            saved.add(insight);
        }

        List<RaceInsight> recent = store.getRecent(SESSION_KEY, InsightStore.MAX_INSIGHTS_PER_SESSION + 10);
        assertThat(recent).hasSize(InsightStore.MAX_INSIGHTS_PER_SESSION);
        assertThat(recent.getFirst()).isEqualTo(saved.getLast());
    }
}
