package com.evanp.f1.core.event;

import java.time.Instant;
import java.util.Objects;

public record RaceEvent(
        long sessionKey, Instant timestamp, RaceEventType type, String summary) {

    public RaceEvent {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
    }
}
