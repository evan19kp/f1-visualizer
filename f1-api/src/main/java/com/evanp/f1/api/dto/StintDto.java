package com.evanp.f1.api.dto;

import com.evanp.f1.core.stint.StintSnapshot;

public record StintDto(
        int driverNumber,
        long sessionKey,
        String compound,
        int stintNumber,
        Integer lapStart,
        Integer lapEnd,
        Integer tyreAgeAtStart) {

    public static StintDto from(StintSnapshot stint) {
        return new StintDto(
                stint.driverNumber(),
                stint.sessionKey(),
                stint.compound(),
                stint.stintNumber(),
                stint.lapStart(),
                stint.lapEnd(),
                stint.tyreAgeAtStart());
    }
}
