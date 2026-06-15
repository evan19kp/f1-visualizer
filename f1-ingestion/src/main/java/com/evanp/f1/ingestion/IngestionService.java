package com.evanp.f1.ingestion;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionBounds;
import com.evanp.f1.ingestion.config.IngestionProperties;
import com.evanp.f1.ingestion.normalize.CoordinateNormalizer;
import com.evanp.f1.ingestion.normalize.NormalizationResult;
import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1LocationResponse;
import com.evanp.f1.ingestion.openf1.OpenF1SessionResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final OpenF1Client openF1Client;
    private final CoordinateNormalizer coordinateNormalizer;
    private final PositionStore positionStore;
    private final IngestionProperties properties;
    private final Clock clock;
    private final SessionMetadataSync sessionMetadataSync;
    private final IngestionStatusService ingestionStatusService;
    private final SessionKeyResolver sessionKeyResolver;

    public IngestionService(
            OpenF1Client openF1Client,
            CoordinateNormalizer coordinateNormalizer,
            PositionStore positionStore,
            IngestionProperties properties,
            Clock clock,
            SessionMetadataSync sessionMetadataSync,
            IngestionStatusService ingestionStatusService,
            SessionKeyResolver sessionKeyResolver) {
        this.openF1Client = openF1Client;
        this.coordinateNormalizer = coordinateNormalizer;
        this.positionStore = positionStore;
        this.properties = properties;
        this.clock = clock;
        this.sessionMetadataSync = sessionMetadataSync;
        this.ingestionStatusService = ingestionStatusService;
        this.sessionKeyResolver = sessionKeyResolver;
    }

    public void pollOnce() {
        if (!properties.enabled()) {
            return;
        }
        try {
            String configKey = properties.sessionKey();
            try {
                sessionMetadataSync.syncIfNeeded(configKey);
            } catch (Exception e) {
                log.warn("Session metadata sync failed for configKey={}: {}", configKey, e.getMessage(), e);
            }
            Optional<Instant> since = resolveSince(configKey);
            Instant until = resolveUntil(since.orElse(Instant.EPOCH));
            List<OpenF1LocationResponse> samples =
                    openF1Client.fetchLocations(configKey, since, Optional.of(until));

            long storeSessionKey = resolveStoreSessionKey(configKey, samples);
            if (storeSessionKey < 0) {
                return;
            }

            if (samples.isEmpty()) {
                advanceCursorThroughEmptyWindow(storeSessionKey, since.orElse(Instant.EPOCH), until);
                ingestionStatusService.recordPollSuccess(storeSessionKey);
                return;
            }

            SessionBounds bounds = positionStore.getBounds(storeSessionKey).orElse(SessionBounds.empty());
            NormalizationResult result = coordinateNormalizer.normalize(samples, bounds);

            List<NormalizedPosition> positionsToSave =
                    bounds.isInitialized() && !result.updatedBounds().equals(bounds)
                            ? mergeWithRenormalizedExisting(bounds, result, storeSessionKey)
                            : result.positions();

            positionStore.savePositions(storeSessionKey, positionsToSave);
            positionStore.saveBounds(storeSessionKey, result.updatedBounds());
            positionStore.appendHistory(storeSessionKey, positionsToSave);

            Instant maxTimestamp =
                    samples.stream().map(OpenF1LocationResponse::date).max(Instant::compareTo).orElseThrow();
            positionStore.savePollCursor(storeSessionKey, maxTimestamp);
            ingestionStatusService.recordPollSuccess(storeSessionKey);
        } catch (Exception e) {
            log.error("Ingestion poll failed: {}", e.getMessage(), e);
            ingestionStatusService.recordOpenF1Error("openf1_error");
        }
    }

    private List<NormalizedPosition> mergeWithRenormalizedExisting(
            SessionBounds oldBounds, NormalizationResult result, long storeSessionKey) {
        Map<Integer, NormalizedPosition> merged = new LinkedHashMap<>();
        for (NormalizedPosition existing : positionStore.getAllPositions(storeSessionKey)) {
            merged.put(
                    existing.driverNumber(),
                    coordinateNormalizer.renormalize(existing, oldBounds, result.updatedBounds()));
        }
        for (NormalizedPosition fresh : result.positions()) {
            merged.put(fresh.driverNumber(), fresh);
        }
        return new ArrayList<>(merged.values());
    }

    private Instant resolveUntil(Instant since) {
        Instant now = clock.instant();
        Instant windowEnd = since.plus(IngestionConstants.POLL_WINDOW);
        return windowEnd.isBefore(now) ? windowEnd : now;
    }

    private void advanceCursorThroughEmptyWindow(long storeSessionKey, Instant since, Instant until) {
        if (until.isAfter(since)) {
            positionStore.savePollCursor(storeSessionKey, until);
        }
    }

    private long resolveStoreSessionKey(String configKey, List<OpenF1LocationResponse> samples) {
        if (!samples.isEmpty()) {
            return isNumericSessionKey(configKey)
                    ? Long.parseLong(configKey)
                    : samples.getFirst().sessionKey();
        }
        return resolveCursorSessionKey(configKey);
    }

    private Optional<Instant> resolveSince(String configKey) {
        long cursorKey = resolveCursorSessionKey(configKey);
        if (cursorKey >= 0) {
            Optional<Instant> cursor = positionStore.getPollCursor(cursorKey);
            if (cursor.isPresent()) {
                return cursor;
            }
            return openF1Client
                    .fetchSession(configKey)
                    .map(OpenF1SessionResponse::dateStart)
                    .or(() -> Optional.of(Instant.EPOCH));
        }
        return Optional.of(Instant.EPOCH);
    }

    private long resolveCursorSessionKey(String configKey) {
        return sessionKeyResolver.resolveNumericKey(configKey);
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
