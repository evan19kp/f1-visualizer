package com.evanp.f1.ai.insight;

import com.evanp.f1.core.event.RaceEventType;
import java.time.Instant;

public record RaceInsight(
        long sessionKey, Instant timestamp, RaceEventType eventType, String commentary) {}
