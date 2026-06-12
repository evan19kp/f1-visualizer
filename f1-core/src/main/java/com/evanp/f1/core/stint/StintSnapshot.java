package com.evanp.f1.core.stint;

public record StintSnapshot(
        int driverNumber,
        long sessionKey,
        String compound,
        int stintNumber,
        Integer lapStart,
        Integer lapEnd,
        Integer tyreAgeAtStart) {}
