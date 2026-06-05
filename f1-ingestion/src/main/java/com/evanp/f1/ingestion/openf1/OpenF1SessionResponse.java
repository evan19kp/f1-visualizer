package com.evanp.f1.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record OpenF1SessionResponse(
        @JsonProperty("session_key") long sessionKey,
        @JsonProperty("meeting_key") long meetingKey,
        @JsonProperty("session_name") String sessionName,
        @JsonProperty("circuit_short_name") String circuitShortName,
        @JsonProperty("date_start") Instant dateStart) {}
