package com.evanp.f1.ingestion;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionTimeRange;
import com.evanp.f1.ingestion.config.IngestionProperties;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true")
public class SessionBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(SessionBootstrapService.class);

    private final IngestionProperties properties;
    private final SessionKeyResolver sessionKeyResolver;
    private final PositionStore positionStore;
    private final SessionMetadataSync sessionMetadataSync;
    private final SessionHistoryBackfillService backfillService;

    public SessionBootstrapService(
            IngestionProperties properties,
            SessionKeyResolver sessionKeyResolver,
            PositionStore positionStore,
            SessionMetadataSync sessionMetadataSync,
            SessionHistoryBackfillService backfillService) {
        this.properties = properties;
        this.sessionKeyResolver = sessionKeyResolver;
        this.positionStore = positionStore;
        this.sessionMetadataSync = sessionMetadataSync;
        this.backfillService = backfillService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapOnStartup() {
        if (!properties.autoBootstrap()) {
            return;
        }

        String configKey = properties.sessionKey();
        long sessionKey = sessionKeyResolver.resolveNumericKey(configKey);
        if (sessionKey < 0) {
            log.warn("Skipping session bootstrap: could not resolve numeric key for {}", configKey);
            return;
        }

        if (positionStore.hasHistory(sessionKey)) {
            log.info("Session {} already has replay history; ensuring display positions", sessionKey);
            ensureDisplayPositions(sessionKey);
            return;
        }

        Thread bootstrapThread = new Thread(() -> runBootstrap(configKey, sessionKey), "session-bootstrap");
        bootstrapThread.setDaemon(true);
        bootstrapThread.start();
    }

    private void runBootstrap(String configKey, long sessionKey) {
        log.info("Bootstrapping session {} from OpenF1 (no replay history in Redis)", sessionKey);
        try {
            sessionMetadataSync.syncIfNeeded(configKey);
            SessionHistoryBackfillService.BackfillResult result = backfillService.backfill(configKey, true);
            if (result.success()) {
                log.info(
                        "Session {} bootstrap complete ({} samples); cars and timeline ready",
                        sessionKey,
                        result.samplesAppended());
            } else {
                log.warn("Session {} bootstrap failed: {}", sessionKey, result.error());
            }
        } catch (Exception e) {
            log.error("Session {} bootstrap failed: {}", sessionKey, e.getMessage(), e);
        }
    }

    private void ensureDisplayPositions(long sessionKey) {
        if (!positionStore.getAllPositions(sessionKey).isEmpty()) {
            return;
        }
        Optional<SessionTimeRange> range = positionStore.getHistoryTimeRange(sessionKey);
        if (range.isEmpty()) {
            return;
        }
        List<NormalizedPosition> frame = positionStore.getFrameAt(sessionKey, range.get().start());
        if (!frame.isEmpty()) {
            positionStore.savePositions(sessionKey, frame);
        }
    }
}
