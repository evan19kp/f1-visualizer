package com.evanp.f1.api.dto;

import com.evanp.f1.ai.insight.RaceInsight;
import com.evanp.f1.core.event.RaceEventType;
import java.time.Instant;

public record RaceInsightDto(
        long sessionKey, Instant timestamp, RaceEventType eventType, String commentary) {

    public static RaceInsightDto from(RaceInsight insight) {
        return new RaceInsightDto(
                insight.sessionKey(),
                insight.timestamp(),
                insight.eventType(),
                insight.commentary());
    }
}
