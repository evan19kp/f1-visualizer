package com.evanp.f1.core.event;

import java.time.Instant;

public record RaceEvent(
        long sessionKey, Instant timestamp, RaceEventType type, String summary) {}
