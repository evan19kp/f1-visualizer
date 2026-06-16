package com.evanp.f1.core.position;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PositionStore {

    void savePositions(long sessionKey, List<NormalizedPosition> positions);

    /** Replace the live position hash and publish the full frame (used by replay). */
    void setPositions(long sessionKey, List<NormalizedPosition> positions);

    Optional<NormalizedPosition> getLatest(long sessionKey, int driverNumber);

    List<NormalizedPosition> getAllPositions(long sessionKey);

    Optional<SessionBounds> getBounds(long sessionKey);

    void saveBounds(long sessionKey, SessionBounds bounds);

    Optional<Instant> getPollCursor(long sessionKey);

    void savePollCursor(long sessionKey, Instant cursor);

    void appendHistory(long sessionKey, List<NormalizedPosition> positions);

    List<NormalizedPosition> getFrameAt(long sessionKey, Instant instant);

    /** Nearest-at-or-before sample per driver merged across all history keyframes up to instant. */
    List<NormalizedPosition> getCompositeFrameAt(long sessionKey, Instant instant);

    boolean hasHistory(long sessionKey);

    Optional<SessionTimeRange> getHistoryTimeRange(long sessionKey);

    /** Remove positions, bounds, poll cursor, and history for a session. Returns deleted Redis key names. */
    List<String> clearSession(long sessionKey);
}
