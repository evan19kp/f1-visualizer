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
import java.time.Duration;
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
    private static final Duration POLL_WINDOW = Duration.ofMinutes(5);

    private final OpenF1Client openF1Client;
    private final CoordinateNormalizer coordinateNormalizer;
    private final PositionStore positionStore;
    private final IngestionProperties properties;

    public IngestionService(
            OpenF1Client openF1Client,
            CoordinateNormalizer coordinateNormalizer,
            PositionStore positionStore,
            IngestionProperties properties) {
        this.openF1Client = openF1Client;
        this.coordinateNormalizer = coordinateNormalizer;
        this.positionStore = positionStore;
        this.properties = properties;
    }

    public void pollOnce() {
        try {
            String configKey = properties.sessionKey();
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

            Instant maxTimestamp =
                    samples.stream().map(OpenF1LocationResponse::date).max(Instant::compareTo).orElseThrow();
            positionStore.savePollCursor(storeSessionKey, maxTimestamp);
        } catch (Exception e) {
            log.error("Ingestion poll failed: {}", e.getMessage(), e);
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
        Instant now = Instant.now();
        Instant windowEnd = since.plus(POLL_WINDOW);
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
