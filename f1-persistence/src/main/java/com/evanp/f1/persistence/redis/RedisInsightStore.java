package com.evanp.f1.persistence.redis;

import com.evanp.f1.ai.insight.InsightStore;
import com.evanp.f1.ai.insight.RaceInsight;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.insights.store", havingValue = "redis")
public class RedisInsightStore implements InsightStore {

    private static final Logger log = LoggerFactory.getLogger(RedisInsightStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisInsightStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(long sessionKey, RaceInsight insight) {
        if (insight == null) {
            throw new IllegalArgumentException("insight must not be null");
        }
        String key = insightsKey(sessionKey);
        redis.opsForList().leftPush(key, toJson(insight));
        redis.opsForList().trim(key, 0, InsightStore.MAX_INSIGHTS_PER_SESSION - 1);
    }

    @Override
    public List<RaceInsight> getRecent(long sessionKey, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        if (limit == 0) {
            return List.of();
        }
        String key = insightsKey(sessionKey);
        List<String> jsonValues = redis.opsForList().range(key, 0, limit - 1L);
        if (jsonValues == null || jsonValues.isEmpty()) {
            return List.of();
        }
        List<RaceInsight> insights = new ArrayList<>(jsonValues.size());
        for (String json : jsonValues) {
            deserialize(json).ifPresent(insights::add);
        }
        return insights;
    }

    static String insightsKey(long sessionKey) {
        return "f1:session:" + sessionKey + ":insights";
    }

    private String toJson(RaceInsight insight) {
        try {
            return objectMapper.writeValueAsString(insight);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize RaceInsight", e);
        }
    }

    private Optional<RaceInsight> deserialize(String json) {
        try {
            return Optional.of(objectMapper.readValue(json, RaceInsight.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize RaceInsight: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
