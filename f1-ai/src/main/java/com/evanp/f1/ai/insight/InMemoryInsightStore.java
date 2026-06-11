package com.evanp.f1.ai.insight;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

@Component
public class InMemoryInsightStore implements InsightStore {

    private final ConcurrentHashMap<Long, Deque<RaceInsight>> bySession = new ConcurrentHashMap<>();

    @Override
    public void save(long sessionKey, RaceInsight insight) {
        if (insight == null) {
            throw new IllegalArgumentException("insight must not be null");
        }
        bySession.computeIfAbsent(sessionKey, ignored -> new ConcurrentLinkedDeque<>()).addFirst(insight);
    }

    @Override
    public List<RaceInsight> getRecent(long sessionKey, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        Deque<RaceInsight> insights = bySession.get(sessionKey);
        if (insights == null) {
            return List.of();
        }
        return insights.stream().limit(limit).toList();
    }
}
