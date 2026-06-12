package com.evanp.f1.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.ai.insight.InsightStore;
import com.evanp.f1.ai.insight.RaceInsight;
import com.evanp.f1.core.event.RaceEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisInsightStoreTest {

    private static final long SESSION_KEY = 9161L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private ObjectMapper objectMapper;
    private RedisInsightStore store;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        store = new RedisInsightStore(redisTemplate, objectMapper);
    }

    @Test
    void save_leftPushesJsonAndTrimsToMaxPerSession() throws Exception {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        RaceInsight insight = sampleInsight("Undercut window opening");

        store.save(SESSION_KEY, insight);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).leftPush(eq("f1:session:" + SESSION_KEY + ":insights"), jsonCaptor.capture());
        verify(listOperations)
                .trim("f1:session:" + SESSION_KEY + ":insights", 0, InsightStore.MAX_INSIGHTS_PER_SESSION - 1);

        RaceInsight roundTrip = objectMapper.readValue(jsonCaptor.getValue(), RaceInsight.class);
        assertThat(roundTrip).isEqualTo(insight);
    }

    @Test
    void save_rejectsNullInsight() {
        assertThatThrownBy(() -> store.save(SESSION_KEY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("insight must not be null");
    }

    @Test
    void getRecent_returnsNewestFirstUpToLimit() throws Exception {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        RaceInsight newest = sampleInsight("Latest call");
        RaceInsight older = sampleInsight("Earlier call");
        when(listOperations.range("f1:session:" + SESSION_KEY + ":insights", 0, 1))
                .thenReturn(List.of(objectMapper.writeValueAsString(newest), objectMapper.writeValueAsString(older)));

        List<RaceInsight> recent = store.getRecent(SESSION_KEY, 2);

        assertThat(recent).containsExactly(newest, older);
    }

    @Test
    void getRecent_returnsEmptyWhenKeyMissing() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("f1:session:" + SESSION_KEY + ":insights", 0, 4)).thenReturn(null);

        assertThat(store.getRecent(SESSION_KEY, 5)).isEmpty();
    }

    @Test
    void getRecent_rejectsNegativeLimit() {
        assertThatThrownBy(() -> store.getRecent(SESSION_KEY, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be non-negative");
    }

    private static RaceInsight sampleInsight(String commentary) {
        return new RaceInsight(
                SESSION_KEY, Instant.parse("2024-03-02T15:00:00Z"), RaceEventType.UNDERCUT, commentary);
    }
}
