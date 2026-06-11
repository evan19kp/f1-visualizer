package com.evanp.f1.ai.service;

import com.evanp.f1.ai.config.OpenAiProperties;
import com.evanp.f1.ai.insight.InsightStore;
import com.evanp.f1.ai.insight.RaceInsight;
import com.evanp.f1.ai.openai.OpenAiClient;
import com.evanp.f1.core.event.RaceEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ConcurrentHashMap<Long, Instant> lastCallBySession = new ConcurrentHashMap<>();

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
        Instant lastCall = lastCallBySession.get(sessionKey);
        if (lastCall != null
                && Duration.between(lastCall, now).getSeconds() < properties.rateLimitSeconds()) {
            log.debug("Rate limit: skipping commentary for session {}", sessionKey);
            return;
        }

        lastCallBySession.put(sessionKey, now);
        Optional<String> commentary = openAiClient.generateCommentary(event);
        commentary.ifPresent(text -> insightStore.save(
                sessionKey,
                new RaceInsight(sessionKey, event.timestamp(), event.type(), text)));
    }
}
