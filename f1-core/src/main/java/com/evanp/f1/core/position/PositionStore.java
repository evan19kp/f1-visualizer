package com.evanp.f1.core.position;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PositionStore {

    void savePositions(long sessionKey, List<NormalizedPosition> positions);

    Optional<NormalizedPosition> getLatest(long sessionKey, int driverNumber);

    List<NormalizedPosition> getAllPositions(long sessionKey);

    Optional<SessionBounds> getBounds(long sessionKey);

    void saveBounds(long sessionKey, SessionBounds bounds);

    Optional<Instant> getPollCursor(long sessionKey);

    void savePollCursor(long sessionKey, Instant cursor);
}
