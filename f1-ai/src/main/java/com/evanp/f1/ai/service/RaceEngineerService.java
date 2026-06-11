package com.evanp.f1.ai.service;

import com.evanp.f1.ai.config.OpenAiProperties;
import com.evanp.f1.ai.insight.InsightStore;
import com.evanp.f1.ai.insight.RaceInsight;
import com.evanp.f1.ai.openai.OpenAiClient;
import com.evanp.f1.core.event.RaceEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RaceEngineerService {

    private static final Logger log = LoggerFactory.getLogger(RaceEngineerService.class);

    private final OpenAiClient openAiClient;
    private final InsightStore insightStore;
    private final OpenAiProperties properties;
    private final Cache<Long, Instant> lastCallBySession = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public RaceEngineerService(
            OpenAiClient openAiClient, InsightStore insightStore, OpenAiProperties properties) {
        this.openAiClient = openAiClient;
        this.insightStore = insightStore;
        this.properties = properties;
    }

    @Async
    public void generateCommentary(RaceEvent event) {
        long sessionKey = event.sessionKey();
        Instant now = Instant.now();
        AtomicBoolean allowed = new AtomicBoolean(false);

        lastCallBySession.asMap().compute(sessionKey, (key, lastCall) -> {
            if (lastCall == null
                    || Duration.between(lastCall, now).getSeconds() >= properties.rateLimitSeconds()) {
                allowed.set(true);
                return now;
            }
            return lastCall;
        });

        if (!allowed.get()) {
            log.debug("Rate limit: skipping commentary for session {}", sessionKey);
            return;
        }

        Optional<String> commentary = openAiClient.generateCommentary(event);
        commentary.ifPresent(text -> insightStore.save(
                sessionKey,
                new RaceInsight(sessionKey, event.timestamp(), event.type(), text)));
    }
}
