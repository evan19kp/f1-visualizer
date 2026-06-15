package com.evanp.f1.ingestion;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionBounds;
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
import org.springframework.stereotype.Service;

@Service
public class SessionHistoryBackfillService {

    private static final Logger log = LoggerFactory.getLogger(SessionHistoryBackfillService.class);

    private final OpenF1Client openF1Client;
    private final CoordinateNormalizer coordinateNormalizer;
    private final PositionStore positionStore;
    private final SessionKeyResolver sessionKeyResolver;
    private final Clock clock;

    public SessionHistoryBackfillService(
            OpenF1Client openF1Client,
            CoordinateNormalizer coordinateNormalizer,
            PositionStore positionStore,
            SessionKeyResolver sessionKeyResolver,
            Clock clock) {
        this.openF1Client = openF1Client;
        this.coordinateNormalizer = coordinateNormalizer;
        this.positionStore = positionStore;
        this.sessionKeyResolver = sessionKeyResolver;
        this.clock = clock;
    }

    public BackfillResult backfill(String configKey) {
        long sessionKey = sessionKeyResolver.resolveNumericKey(configKey);
        if (sessionKey < 0) {
            return new BackfillResult(sessionKey, 0, false, "session_not_found");
        }

        Optional<OpenF1SessionResponse> session = openF1Client.fetchSession(String.valueOf(sessionKey));
        if (session.isEmpty()) {
            return new BackfillResult(sessionKey, 0, false, "session_metadata_unavailable");
        }
        Instant start = session.get().dateStart();
        Instant end = session.get().dateEnd() != null ? session.get().dateEnd() : clock.instant();
        if (end.isBefore(start)) {
            end = clock.instant();
        }

        Instant cursor = start;
        int framesAppended = 0;
        SessionBounds bounds = positionStore.getBounds(sessionKey).orElse(SessionBounds.empty());

        while (cursor.isBefore(end)) {
            Instant windowEnd = cursor.plus(IngestionConstants.POLL_WINDOW);
            if (windowEnd.isAfter(end)) {
                windowEnd = end;
            }
            List<OpenF1LocationResponse> samples =
                    openF1Client.fetchLocations(String.valueOf(sessionKey), Optional.of(cursor), Optional.of(windowEnd));
            if (samples.isEmpty()) {
                cursor = windowEnd;
                continue;
            }

            NormalizationResult result = coordinateNormalizer.normalize(samples, bounds);
            bounds = result.updatedBounds();
            List<NormalizedPosition> positions = dedupeLatestPerDriver(result.positions());
            positionStore.appendHistory(sessionKey, positions);
            positionStore.saveBounds(sessionKey, bounds);
            framesAppended += positions.size();

            Instant maxTimestamp =
                    samples.stream().map(OpenF1LocationResponse::date).max(Instant::compareTo).orElse(cursor);
            cursor = maxTimestamp.isAfter(cursor) ? maxTimestamp : windowEnd;
        }

        positionStore.savePollCursor(sessionKey, end);
        List<NormalizedPosition> lastFrame = positionStore.getFrameAt(sessionKey, end);
        if (!lastFrame.isEmpty()) {
            positionStore.savePositions(sessionKey, lastFrame);
        }

        log.info("Backfilled session {} with {} position samples", sessionKey, framesAppended);
        return new BackfillResult(sessionKey, framesAppended, true, "none");
    }

    private static List<NormalizedPosition> dedupeLatestPerDriver(List<NormalizedPosition> positions) {
        Map<Integer, NormalizedPosition> latest = new LinkedHashMap<>();
        for (NormalizedPosition position : positions) {
            latest.merge(position.driverNumber(), position, (existing, candidate) ->
                    candidate.timestamp().isAfter(existing.timestamp()) ? candidate : existing);
        }
        return new ArrayList<>(latest.values());
    }

    public record BackfillResult(long sessionKey, int samplesAppended, boolean success, String error) {}
}
