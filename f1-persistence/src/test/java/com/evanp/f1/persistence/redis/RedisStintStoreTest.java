package com.evanp.f1.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.core.stint.StintSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisStintStoreTest {

    private static final long SESSION_KEY = 9161L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ObjectMapper objectMapper;
    private RedisStintStore store;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        store = new RedisStintStore(redisTemplate, objectMapper);
    }

    @Test
    void save_usesExpectedRedisKeyAndJson() throws Exception {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        StintSnapshot stint = new StintSnapshot(44, SESSION_KEY, "SOFT", 2, 10, null, 0);

        store.save(SESSION_KEY, List.of(stint));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(hashOperations)
                .put(eq("f1:session:" + SESSION_KEY + ":stints"), eq("44"), jsonCaptor.capture());

        StintSnapshot roundTrip = objectMapper.readValue(jsonCaptor.getValue(), StintSnapshot.class);
        assertThat(roundTrip).isEqualTo(stint);
    }

    @Test
    void save_emptyList_doesNotTouchRedis() {
        store.save(SESSION_KEY, List.of());

        verify(redisTemplate, org.mockito.Mockito.never()).opsForHash();
    }

    @Test
    void getLatest_roundTripsJson() throws Exception {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        StintSnapshot stint = new StintSnapshot(44, SESSION_KEY, "MEDIUM", 1, 1, 15, 3);
        String json = objectMapper.writeValueAsString(stint);

        when(hashOperations.get("f1:session:" + SESSION_KEY + ":stints", "44")).thenReturn(json);

        Optional<StintSnapshot> latest = store.getLatest(SESSION_KEY, 44);

        assertThat(latest).contains(stint);
    }

    @Test
    void getLatest_returnsEmptyWhenFieldMissing() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("f1:session:" + SESSION_KEY + ":stints", "99")).thenReturn(null);

        Optional<StintSnapshot> latest = store.getLatest(SESSION_KEY, 99);

        assertThat(latest).isEmpty();
    }

    @Test
    void getAll_returnsEveryStoredDriver() throws Exception {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        StintSnapshot first = new StintSnapshot(1, SESSION_KEY, "SOFT", 1, 1, 20, 0);
        StintSnapshot second = new StintSnapshot(44, SESSION_KEY, "HARD", 2, 21, null, 0);
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("1", objectMapper.writeValueAsString(first));
        entries.put("44", objectMapper.writeValueAsString(second));
        when(hashOperations.entries("f1:session:" + SESSION_KEY + ":stints")).thenReturn(entries);

        List<StintSnapshot> stints = store.getAll(SESSION_KEY);

        assertThat(stints).hasSize(2);
        assertThat(stints).extracting(StintSnapshot::driverNumber).containsExactlyInAnyOrder(1, 44);
    }
}
