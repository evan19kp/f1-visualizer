package com.evanp.f1.api.dev;

import com.evanp.f1.api.dto.SessionResetResponse;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.ingestion.IngestionService;
import com.evanp.f1.ingestion.config.IngestionProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SessionResetService {

    private static final Logger log = LoggerFactory.getLogger(SessionResetService.class);

    private final PositionStore positionStore;
    private final IngestionProperties ingestionProperties;
    private final IngestionService ingestionService;

    public SessionResetService(
            PositionStore positionStore,
            IngestionProperties ingestionProperties,
            IngestionService ingestionService) {
        this.positionStore = positionStore;
        this.ingestionProperties = ingestionProperties;
        this.ingestionService = ingestionService;
    }

    public SessionResetResponse reset(long sessionKey) {
        List<String> clearedKeys = positionStore.clearSession(sessionKey);
        boolean reingestTriggered = false;
        if (ingestionProperties.enabled() && matchesIngestionKey(sessionKey)) {
            try {
                ingestionService.pollOnce();
                reingestTriggered = true;
            } catch (RuntimeException exception) {
                log.warn(
                        "Session {} cache cleared but re-ingest failed: {}",
                        sessionKey,
                        exception.getMessage(),
                        exception);
            }
        }
        return new SessionResetResponse(clearedKeys, reingestTriggered);
    }

    private boolean matchesIngestionKey(long sessionKey) {
        String configKey = ingestionProperties.sessionKey();
        if (configKey == null || configKey.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(configKey) == sessionKey;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
