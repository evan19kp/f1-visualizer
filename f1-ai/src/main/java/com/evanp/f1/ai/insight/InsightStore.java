package com.evanp.f1.ai.insight;

import java.util.List;

/**
 * Persists race-engineer commentary. Default {@link InMemoryInsightStore} is for dev;
 * Agent C may replace with a Redis-backed implementation.
 */
public interface InsightStore {

    void save(long sessionKey, RaceInsight insight);

    List<RaceInsight> getRecent(long sessionKey, int limit);
}
