package com.evanp.f1.ai.insight;

import com.evanp.f1.core.event.RaceEventType;
import java.time.Instant;
import java.util.Objects;

public record RaceInsight(
        long sessionKey, Instant timestamp, RaceEventType eventType, String commentary) {

    public RaceInsight {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(commentary, "commentary must not be null");
    }
}
