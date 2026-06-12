package com.evanp.f1.ai.insight;

import java.util.List;

/**
 * Persists race-engineer commentary. {@link InMemoryInsightStore} is the default for local dev;
 * production uses {@code com.evanp.f1.persistence.redis.RedisInsightStore} via {@code app.insights.store=redis}.
 */
public interface InsightStore {

    int MAX_INSIGHTS_PER_SESSION = 100;

    void save(long sessionKey, RaceInsight insight);

    List<RaceInsight> getRecent(long sessionKey, int limit);
}
