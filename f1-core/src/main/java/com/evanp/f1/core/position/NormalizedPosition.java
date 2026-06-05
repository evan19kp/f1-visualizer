package com.evanp.f1.core.position;

import java.time.Instant;

public record NormalizedPosition(
        int driverNumber,
        long sessionKey,
        Instant timestamp,
        double x,
        double y,
        double z) {}
