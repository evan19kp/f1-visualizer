package com.evanp.f1.persistence.redis;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionBounds;
import com.evanp.f1.core.position.SessionTimeRange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Component
public class RedisPositionStore implements PositionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisPositionStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisPositionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void savePositions(long sessionKey, List<NormalizedPosition> positions) {
        if (positions.isEmpty()) {
            return;
        }
        String key = positionsKey(sessionKey);
        for (NormalizedPosition position : positions) {
            redis.opsForHash().put(key, String.valueOf(position.driverNumber()), toJson(position));
        }
        publishPositions(sessionKey, positions);
    }

    @Override
    public void setPositions(long sessionKey, List<NormalizedPosition> positions) {
        String key = positionsKey(sessionKey);
        redis.delete(key);
        if (positions.isEmpty()) {
            return;
        }
        for (NormalizedPosition position : positions) {
            redis.opsForHash().put(key, String.valueOf(position.driverNumber()), toJson(position));
        }
        publishPositions(sessionKey, positions);
    }

    private void publishPositions(long sessionKey, List<NormalizedPosition> positions) {
        try {
            redis.convertAndSend(pubSubChannel(sessionKey), objectMapper.writeValueAsString(positions));
        } catch (Exception e) {
            log.warn("Failed to publish positions for session {}: {}", sessionKey, e.getMessage());
        }
    }

    @Override
    public Optional<NormalizedPosition> getLatest(long sessionKey, int driverNumber) {
        String json = (String) redis.opsForHash().get(positionsKey(sessionKey), String.valueOf(driverNumber));
        if (json == null) {
            return Optional.empty();
        }
        return deserialize(json, NormalizedPosition.class);
    }

    @Override
    public List<NormalizedPosition> getAllPositions(long sessionKey) {
        Map<Object, Object> entries = redis.opsForHash().entries(positionsKey(sessionKey));
        if (entries.isEmpty()) {
            return List.of();
        }
        List<NormalizedPosition> positions = new ArrayList<>(entries.size());
        for (Object json : entries.values()) {
            deserialize((String) json, NormalizedPosition.class).ifPresent(positions::add);
        }
        return positions;
    }

    @Override
    public Optional<SessionBounds> getBounds(long sessionKey) {
        String json = redis.opsForValue().get(boundsKey(sessionKey));
        if (json == null) {
            return Optional.empty();
        }
        return deserialize(json, SessionBounds.class);
    }

    @Override
    public void saveBounds(long sessionKey, SessionBounds bounds) {
        redis.opsForValue().set(boundsKey(sessionKey), toJson(bounds));
    }

    @Override
    public Optional<Instant> getPollCursor(long sessionKey) {
        String value = redis.opsForValue().get(pollCursorKey(sessionKey));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (Exception e) {
            log.warn("Failed to parse poll cursor for session {}: {}", sessionKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void savePollCursor(long sessionKey, Instant cursor) {
        redis.opsForValue().set(pollCursorKey(sessionKey), cursor.toString());
    }

    @Override
    public void appendHistory(long sessionKey, List<NormalizedPosition> positions) {
        if (positions.isEmpty()) {
            return;
        }
        Map<Instant, Map<Integer, NormalizedPosition>> byTime = new LinkedHashMap<>();
        for (NormalizedPosition position : positions) {
            byTime.computeIfAbsent(position.timestamp(), ignored -> new LinkedHashMap<>())
                    .put(position.driverNumber(), position);
        }
        String key = historyKey(sessionKey);
        ZSetOperations<String, String> zset = redis.opsForZSet();
        for (Map.Entry<Instant, Map<Integer, NormalizedPosition>> entry : byTime.entrySet()) {
            double score = entry.getKey().toEpochMilli();
            List<NormalizedPosition> frame = List.copyOf(entry.getValue().values());
            zset.removeRangeByScore(key, score, score);
            zset.add(key, toJson(frame), score);
        }
    }

    @Override
    public List<NormalizedPosition> getFrameAt(long sessionKey, Instant instant) {
        String key = historyKey(sessionKey);
        double score = instant.toEpochMilli();
        Set<String> atOrBefore =
                redis.opsForZSet().reverseRangeByScore(key, Double.NEGATIVE_INFINITY, score, 0, 1);
        if (atOrBefore == null || atOrBefore.isEmpty()) {
            Set<String> after = redis.opsForZSet().rangeByScore(key, score, Double.POSITIVE_INFINITY, 0, 1);
            if (after == null || after.isEmpty()) {
                return List.of();
            }
            return deserializeFrame(after.iterator().next());
        }
        return deserializeFrame(atOrBefore.iterator().next());
    }

    @Override
    public List<NormalizedPosition> getCompositeFrameAt(long sessionKey, Instant instant) {
        String key = historyKey(sessionKey);
        double score = instant.toEpochMilli();
        Set<String> members =
                redis.opsForZSet().reverseRangeByScore(key, Double.NEGATIVE_INFINITY, score);
        if (members == null || members.isEmpty()) {
            Set<String> after = redis.opsForZSet().rangeByScore(key, score, Double.POSITIVE_INFINITY, 0, 1);
            if (after == null || after.isEmpty()) {
                return List.of();
            }
            return deserializeFrame(after.iterator().next());
        }
        Map<Integer, NormalizedPosition> latestByDriver = new TreeMap<>();
        for (String member : members) {
            for (NormalizedPosition position : deserializeFrame(member)) {
                latestByDriver.merge(
                        position.driverNumber(),
                        position,
                        (existing, incoming) ->
                                incoming.timestamp().isAfter(existing.timestamp()) ? incoming : existing);
            }
        }
        return latestByDriver.values().stream()
                .sorted(Comparator.comparingInt(NormalizedPosition::driverNumber))
                .toList();
    }

    @Override
    public boolean hasHistory(long sessionKey) {
        Long size = redis.opsForZSet().size(historyKey(sessionKey));
        return size != null && size > 0;
    }

    @Override
    public Optional<SessionTimeRange> getHistoryTimeRange(long sessionKey) {
        String key = historyKey(sessionKey);
        ZSetOperations<String, String> zset = redis.opsForZSet();
        Set<String> first = zset.range(key, 0, 0);
        Set<String> last = zset.reverseRange(key, 0, 0);
        if (first == null || first.isEmpty() || last == null || last.isEmpty()) {
            return Optional.empty();
        }
        Double minScore = zset.score(key, first.iterator().next());
        Double maxScore = zset.score(key, last.iterator().next());
        if (minScore == null || maxScore == null) {
            return Optional.empty();
        }
        return Optional.of(new SessionTimeRange(
                Instant.ofEpochMilli(minScore.longValue()), Instant.ofEpochMilli(maxScore.longValue())));
    }

    @Override
    public List<String> clearSession(long sessionKey) {
        List<String> keys = List.of(
                positionsKey(sessionKey), boundsKey(sessionKey), pollCursorKey(sessionKey), historyKey(sessionKey));
        List<String> cleared = new ArrayList<>(keys.size());
        for (String key : keys) {
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                cleared.add(key);
            }
        }
        if (!cleared.isEmpty()) {
            redis.delete(cleared);
        }
        return cleared;
    }

    static String positionsKey(long sessionKey) {
        return "f1:session:" + sessionKey + ":positions";
    }

    static String boundsKey(long sessionKey) {
        return "f1:session:" + sessionKey + ":bounds";
    }

    static String pollCursorKey(long sessionKey) {
        return "f1:session:" + sessionKey + ":poll_cursor";
    }

    static String historyKey(long sessionKey) {
        return "f1:session:" + sessionKey + ":history";
    }

    static String pubSubChannel(long sessionKey) {
        return "f1:pubsub:session:" + sessionKey + ":positions";
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

    private List<NormalizedPosition> deserializeFrame(String json) {
        try {
            List<NormalizedPosition> frame =
                    objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return frame != null ? frame : List.of();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize history frame: {}", e.getMessage());
            return List.of();
        }
    }
}
