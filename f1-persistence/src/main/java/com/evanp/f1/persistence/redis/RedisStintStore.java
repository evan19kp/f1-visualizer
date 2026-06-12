package com.evanp.f1.persistence.redis;

import com.evanp.f1.core.stint.StintSnapshot;
import com.evanp.f1.core.stint.StintStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisStintStore implements StintStore {

    private static final Logger log = LoggerFactory.getLogger(RedisStintStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisStintStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(long sessionKey, List<StintSnapshot> stints) {
        if (stints.isEmpty()) {
            return;
        }
        String key = stintsKey(sessionKey);
        for (StintSnapshot stint : stints) {
            redis.opsForHash().put(key, String.valueOf(stint.driverNumber()), toJson(stint));
        }
    }

    @Override
    public Optional<StintSnapshot> getLatest(long sessionKey, int driverNumber) {
        String json = (String) redis.opsForHash().get(stintsKey(sessionKey), String.valueOf(driverNumber));
        if (json == null) {
            return Optional.empty();
        }
        return deserialize(json, StintSnapshot.class);
    }

    @Override
    public List<StintSnapshot> getAll(long sessionKey) {
        Map<Object, Object> entries = redis.opsForHash().entries(stintsKey(sessionKey));
        if (entries.isEmpty()) {
            return List.of();
        }
        List<StintSnapshot> stints = new ArrayList<>(entries.size());
        for (Object json : entries.values()) {
            deserialize((String) json, StintSnapshot.class).ifPresent(stints::add);
        }
        return stints;
    }

    /** Redis hash: one field per driver, value is the latest {@link StintSnapshot} JSON. */
    static String stintsKey(long sessionKey) {
        return "f1:session:" + sessionKey + ":stints";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + value.getClass().getSimpleName(), e);
        }
    }

    private <T> Optional<T> deserialize(String json, Class<T> type) {
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize {}: {}", type.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }
}
