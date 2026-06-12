package com.evanp.f1.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenF1StintResponse(
        String compound,
        @JsonProperty("driver_number") int driverNumber,
        @JsonProperty("lap_end") Integer lapEnd,
        @JsonProperty("lap_start") Integer lapStart,
        @JsonProperty("meeting_key") long meetingKey,
        @JsonProperty("session_key") long sessionKey,
        @JsonProperty("stint_number") int stintNumber,
        @JsonProperty("tyre_age_at_start") Integer tyreAgeAtStart) {}
