package com.evanp.f1.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.SessionBounds;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
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
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisPositionStoreTest {

    private static final long SESSION_KEY = 9158L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RedisPositionStore store;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        store = new RedisPositionStore(redisTemplate, objectMapper);
    }

    @Test
    void savePositions_usesExpectedRedisKeyAndJson() throws Exception {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Instant timestamp = Instant.parse("2024-03-02T15:00:00Z");
        NormalizedPosition position =
                new NormalizedPosition(44, SESSION_KEY, timestamp, 1.2, 3.4, 5.6);

        store.savePositions(SESSION_KEY, List.of(position));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(hashOperations)
                .put(
                        eq("f1:session:" + SESSION_KEY + ":positions"),
                        eq("44"),
                        jsonCaptor.capture());

        NormalizedPosition roundTrip =
                objectMapper.readValue(jsonCaptor.getValue(), NormalizedPosition.class);
        assertThat(roundTrip).isEqualTo(position);
        verify(redisTemplate)
                .convertAndSend(
                        eq("f1:pubsub:session:" + SESSION_KEY + ":positions"),
                        eq(objectMapper.writeValueAsString(List.of(position))));
    }

    @Test
    void savePositions_publishesJsonArrayToPubSubChannel() throws Exception {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        long sessionKey = 9161L;
        Instant timestamp = Instant.parse("2024-03-02T15:00:00Z");
        NormalizedPosition first = new NormalizedPosition(1, sessionKey, timestamp, 0.1, 0.2, 0.3);
        NormalizedPosition second = new NormalizedPosition(44, sessionKey, timestamp, 0.4, 0.5, 0.6);
        List<NormalizedPosition> batch = List.of(first, second);

        store.savePositions(sessionKey, batch);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate)
                .convertAndSend(eq("f1:pubsub:session:9161:positions"), messageCaptor.capture());

        List<NormalizedPosition> published =
                objectMapper.readValue(messageCaptor.getValue(), new TypeReference<>() {});
        assertThat(published).containsExactly(first, second);
    }

    @Test
    void savePositions_emptyList_doesNotPublish() {
        store.savePositions(SESSION_KEY, List.of());

        verify(redisTemplate, never()).convertAndSend(any(), any());
        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    void getLatest_roundTripsJson() throws Exception {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Instant timestamp = Instant.parse("2024-03-02T15:00:01Z");
        NormalizedPosition position =
                new NormalizedPosition(1, SESSION_KEY, timestamp, 0.1, 0.2, 0.3);
        String json = objectMapper.writeValueAsString(position);

        when(hashOperations.get("f1:session:" + SESSION_KEY + ":positions", "1"))
                .thenReturn(json);

        Optional<NormalizedPosition> latest = store.getLatest(SESSION_KEY, 1);

        assertThat(latest).contains(position);
    }

    @Test
    void getLatest_returnsEmptyWhenFieldMissing() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("f1:session:" + SESSION_KEY + ":positions", "99"))
                .thenReturn(null);

        Optional<NormalizedPosition> latest = store.getLatest(SESSION_KEY, 99);

        assertThat(latest).isEmpty();
    }

    @Test
    void getAllPositions_returnsEveryStoredDriver() throws Exception {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Instant timestamp = Instant.parse("2024-03-02T15:00:00Z");
        NormalizedPosition first = new NormalizedPosition(1, SESSION_KEY, timestamp, 0.1, 0.2, 0.3);
        NormalizedPosition second = new NormalizedPosition(44, SESSION_KEY, timestamp, 0.4, 0.5, 0.6);
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("1", objectMapper.writeValueAsString(first));
        entries.put("44", objectMapper.writeValueAsString(second));
        when(hashOperations.entries("f1:session:" + SESSION_KEY + ":positions")).thenReturn(entries);

        List<NormalizedPosition> positions = store.getAllPositions(SESSION_KEY);

        assertThat(positions).hasSize(2);
        assertThat(positions).extracting(NormalizedPosition::driverNumber).containsExactlyInAnyOrder(1, 44);
    }

    @Test
    void saveBounds_usesExpectedRedisKey() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        SessionBounds bounds = SessionBounds.empty().expand(1.0, 2.0, 3.0);
        String key = "f1:session:" + SESSION_KEY + ":bounds";

        store.saveBounds(SESSION_KEY, bounds);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(key), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue()).contains("\"minX\":1.0");
    }

    @Test
    void getBounds_returnsEmptyWhenKeyMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("f1:session:" + SESSION_KEY + ":bounds")).thenReturn(null);

        Optional<SessionBounds> bounds = store.getBounds(SESSION_KEY);

        assertThat(bounds).isEmpty();
    }

    @Test
    void saveAndGetPollCursor_usesIso8601String() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Instant cursor = Instant.parse("2024-03-02T15:00:02Z");

        store.savePollCursor(SESSION_KEY, cursor);

        verify(valueOperations)
                .set("f1:session:" + SESSION_KEY + ":poll_cursor", "2024-03-02T15:00:02Z");

        when(valueOperations.get("f1:session:" + SESSION_KEY + ":poll_cursor"))
                .thenReturn("2024-03-02T15:00:02Z");

        Optional<Instant> retrieved = store.getPollCursor(SESSION_KEY);

        assertThat(retrieved).contains(cursor);
    }

    @Test
    void clearSession_deletesExistingKeysOnly() {
        String positionsKey = "f1:session:" + SESSION_KEY + ":positions";
        String boundsKey = "f1:session:" + SESSION_KEY + ":bounds";
        String pollCursorKey = "f1:session:" + SESSION_KEY + ":poll_cursor";
        String historyKey = "f1:session:" + SESSION_KEY + ":history";
        when(redisTemplate.hasKey(positionsKey)).thenReturn(true);
        when(redisTemplate.hasKey(boundsKey)).thenReturn(true);
        when(redisTemplate.hasKey(pollCursorKey)).thenReturn(false);
        when(redisTemplate.hasKey(historyKey)).thenReturn(true);

        List<String> cleared = store.clearSession(SESSION_KEY);

        verify(redisTemplate).delete(List.of(positionsKey, boundsKey, historyKey));
        assertThat(cleared).containsExactly(positionsKey, boundsKey, historyKey);
    }
}
