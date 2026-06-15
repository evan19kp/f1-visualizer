package com.evanp.f1.ingestion;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.persistence.session.RaceSessionEntity;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionMetadataSyncTest {

    private static final long SESSION_KEY = 9161L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-03-02T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private OpenF1Client openF1Client;

    @Mock
    private RaceSessionRepository raceSessionRepository;

    @Mock
    private SessionKeyResolver sessionKeyResolver;

    private SessionMetadataSync sessionMetadataSync;

    @BeforeEach
    void setUp() {
        sessionMetadataSync = new SessionMetadataSync(openF1Client, raceSessionRepository, sessionKeyResolver, CLOCK);
    }

    @Test
    void syncIfNeeded_skipsOpenF1WhenDbRowExistsForNumericKey() {
        when(sessionKeyResolver.resolveNumericKey("9161")).thenReturn(SESSION_KEY);
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.of(RaceSessionEntity.newInstance()));

        sessionMetadataSync.syncIfNeeded("9161");

        verify(openF1Client, never()).fetchSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void syncIfNeeded_fetchesWhenDbRowMissing() {
        when(sessionKeyResolver.resolveNumericKey("9161")).thenReturn(SESSION_KEY);
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.empty());

        sessionMetadataSync.syncIfNeeded("9161");

        verify(openF1Client).fetchSession("9161");
    }
}
