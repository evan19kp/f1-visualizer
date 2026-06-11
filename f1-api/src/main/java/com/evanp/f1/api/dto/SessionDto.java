package com.evanp.f1.api.dto;

import com.evanp.f1.persistence.session.RaceSessionEntity;
import java.time.Instant;

public record SessionDto(
        long sessionKey,
        long meetingKey,
        String sessionName,
        String circuitName,
        Instant dateStart) {

    public static SessionDto from(RaceSessionEntity entity) {
        return new SessionDto(
                entity.getSessionKey(),
                entity.getMeetingKey(),
                entity.getSessionName(),
                entity.getCircuitName(),
                entity.getDateStart());
    }
}
