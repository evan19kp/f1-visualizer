package com.evanp.f1.ingestion;

import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1SessionResponse;
import com.evanp.f1.persistence.session.RaceSessionEntity;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SessionMetadataSync {

    private final OpenF1Client openF1Client;
    private final RaceSessionRepository raceSessionRepository;
    private final Clock clock;

    public SessionMetadataSync(
            OpenF1Client openF1Client, RaceSessionRepository raceSessionRepository, Clock clock) {
        this.openF1Client = openF1Client;
        this.raceSessionRepository = raceSessionRepository;
        this.clock = clock;
    }

    public void syncIfNeeded(String configKey) {
        openF1Client.fetchSession(configKey).ifPresent(this::upsert);
    }

    private void upsert(OpenF1SessionResponse session) {
        Instant now = clock.instant();
        Optional<RaceSessionEntity> existing = raceSessionRepository.findById(session.sessionKey());
        RaceSessionEntity entity = existing.orElseGet(RaceSessionEntity::newInstance);
        boolean isNew = existing.isEmpty();
        boolean createdAtSet = entity.getCreatedAt() == null;

        boolean changed = isNew
                || createdAtSet
                || !Objects.equals(entity.getMeetingKey(), session.meetingKey())
                || !Objects.equals(entity.getSessionName(), session.sessionName())
                || !Objects.equals(entity.getCircuitName(), session.circuitShortName())
                || !Objects.equals(entity.getDateStart(), session.dateStart());

        if (!changed) {
            return;
        }

        if (createdAtSet) {
            entity.setCreatedAt(now);
        }
        entity.setSessionKey(session.sessionKey());
        entity.setMeetingKey(session.meetingKey());
        entity.setSessionName(session.sessionName());
        entity.setCircuitName(session.circuitShortName());
        entity.setDateStart(session.dateStart());
        raceSessionRepository.save(entity);
    }
}
