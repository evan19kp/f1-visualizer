package com.evanp.f1.ingestion.ai;

import com.evanp.f1.ai.service.RaceEngineerService;
import com.evanp.f1.core.event.RaceEvent;
import com.evanp.f1.ingestion.config.IngestionProperties;
import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1RaceControlResponse;
import com.evanp.f1.ingestion.openf1.OpenF1SessionResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class RaceControlPoller {

    private static final Logger log = LoggerFactory.getLogger(RaceControlPoller.class);

    private final OpenF1Client openF1Client;
    private final RaceControlEventDetector eventDetector;
    private final RaceEngineerService raceEngineerService;
    private final IngestionProperties ingestionProperties;
    private final ConcurrentHashMap<Long, Instant> cursorBySession = new ConcurrentHashMap<>();

    public RaceControlPoller(
            OpenF1Client openF1Client,
            RaceControlEventDetector eventDetector,
            RaceEngineerService raceEngineerService,
            IngestionProperties ingestionProperties) {
        this.openF1Client = openF1Client;
        this.eventDetector = eventDetector;
        this.raceEngineerService = raceEngineerService;
        this.ingestionProperties = ingestionProperties;
    }

    @Scheduled(fixedDelayString = "${app.ai.poll-interval-ms:5000}")
    public void poll() {
        try {
            String configKey = ingestionProperties.sessionKey();
            long sessionKey = resolveSessionKey(configKey);
            if (sessionKey < 0) {
                return;
            }

            Optional<Instant> since = Optional.ofNullable(cursorBySession.get(sessionKey));
            List<OpenF1RaceControlResponse> messages = openF1Client.fetchRaceControl(configKey, since);
            for (OpenF1RaceControlResponse message : messages) {
                advanceCursor(sessionKey, message.date());
                eventDetector.detect(message).ifPresent(this::fireCommentary);
            }
        } catch (Exception e) {
            log.error("Race control poll failed: {}", e.getMessage(), e);
        }
    }

    private void fireCommentary(RaceEvent event) {
        raceEngineerService.generateCommentary(event);
    }

    private void advanceCursor(long sessionKey, Instant timestamp) {
        if (timestamp == null) {
            return;
        }
        cursorBySession.merge(sessionKey, timestamp, (existing, incoming) -> incoming.isAfter(existing) ? incoming : existing);
    }

    private long resolveSessionKey(String configKey) {
        if (isNumericSessionKey(configKey)) {
            return Long.parseLong(configKey);
        }
        return openF1Client.fetchSession(configKey).map(OpenF1SessionResponse::sessionKey).orElse(-1L);
    }

    private static boolean isNumericSessionKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return false;
        }
        for (int i = 0; i < sessionKey.length(); i++) {
            if (!Character.isDigit(sessionKey.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
