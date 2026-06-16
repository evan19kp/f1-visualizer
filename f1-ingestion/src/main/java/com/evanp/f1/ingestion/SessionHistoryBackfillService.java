package com.evanp.f1.ingestion;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionBounds;
import com.evanp.f1.core.position.SessionTimeRange;
import com.evanp.f1.ingestion.normalize.CoordinateNormalizer;
import com.evanp.f1.ingestion.normalize.NormalizationResult;
import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1LocationResponse;
import com.evanp.f1.ingestion.openf1.OpenF1SessionResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
        return backfill(configKey, false);
    }

    public BackfillResult backfill(String configKey, boolean paceRequests) {
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
            if (paceRequests) {
                awaitOpenF1Availability();
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
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
            positionStore.appendHistory(sessionKey, result.positions());
            positionStore.saveBounds(sessionKey, bounds);
            framesAppended += result.positions().size();

            Instant maxTimestamp =
                    samples.stream().map(OpenF1LocationResponse::date).max(Instant::compareTo).orElse(cursor);
            cursor = maxTimestamp.isAfter(cursor) ? maxTimestamp : windowEnd;
        }

        positionStore.savePollCursor(sessionKey, end);
        saveDisplayPositions(sessionKey, start, end);

        log.info("Backfilled session {} with {} position samples", sessionKey, framesAppended);
        return new BackfillResult(sessionKey, framesAppended, true, "none");
    }

    private void awaitOpenF1Availability() {
        while (!Thread.currentThread().isInterrupted() && openF1Client.isRateLimited()) {
            if (sleepBriefly(1000L)) {
                return;
            }
        }
        if (!Thread.currentThread().isInterrupted()) {
            sleepBriefly(2500L);
        }
    }

    private static boolean sleepBriefly(long millis) {
        try {
            Thread.sleep(millis);
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private void saveDisplayPositions(long sessionKey, Instant start, Instant end) {
        Optional<SessionTimeRange> range = positionStore.getHistoryTimeRange(sessionKey);
        Instant displayInstant = range.map(SessionTimeRange::start).orElse(start);
        List<NormalizedPosition> frame = positionStore.getFrameAt(sessionKey, displayInstant);
        if (frame.isEmpty()) {
            frame = positionStore.getFrameAt(sessionKey, end);
        }
        if (!frame.isEmpty()) {
            positionStore.savePositions(sessionKey, frame);
        }
    }

    public record BackfillResult(long sessionKey, int samplesAppended, boolean success, String error) {}
}
