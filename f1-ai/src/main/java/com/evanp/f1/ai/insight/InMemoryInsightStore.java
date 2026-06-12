package com.evanp.f1.ai.insight;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.insights.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryInsightStore implements InsightStore {

    private final ConcurrentHashMap<Long, Deque<RaceInsight>> bySession = new ConcurrentHashMap<>();

    @Override
    public void save(long sessionKey, RaceInsight insight) {
        if (insight == null) {
            throw new IllegalArgumentException("insight must not be null");
        }
        Deque<RaceInsight> insights =
                bySession.computeIfAbsent(sessionKey, ignored -> new ConcurrentLinkedDeque<>());
        insights.addFirst(insight);
        while (insights.size() > InsightStore.MAX_INSIGHTS_PER_SESSION) {
            insights.pollLast();
        }
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
