package com.evanp.f1.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record OpenF1RaceControlResponse(
        Instant date,
        String category,
        String flag,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("lap_number") Integer lapNumber,
        @JsonProperty("meeting_key") long meetingKey,
        String message,
        String scope,
        Integer sector,
        @JsonProperty("session_key") long sessionKey) {}
