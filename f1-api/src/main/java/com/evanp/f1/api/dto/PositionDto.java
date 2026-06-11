package com.evanp.f1.api.dto;

import com.evanp.f1.core.position.NormalizedPosition;
import java.time.Instant;

public record PositionDto(
        int driverNumber,
        long sessionKey,
        Instant timestamp,
        double x,
        double y,
        double z) {

    public static PositionDto from(NormalizedPosition position) {
        return new PositionDto(
                position.driverNumber(),
                position.sessionKey(),
                position.timestamp(),
                position.x(),
                position.y(),
                position.z());
    }
}
