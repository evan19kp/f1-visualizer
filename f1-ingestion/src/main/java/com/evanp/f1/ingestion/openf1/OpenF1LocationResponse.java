package com.evanp.f1.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record OpenF1LocationResponse(
        Instant date,
        @JsonProperty("driver_number") int driverNumber,
        @JsonProperty("meeting_key") long meetingKey,
        @JsonProperty("session_key") long sessionKey,
        double x,
        double y,
        double z) {}
