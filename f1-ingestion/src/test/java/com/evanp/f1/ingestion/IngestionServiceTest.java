package com.evanp.f1.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionBounds;
import com.evanp.f1.ingestion.config.IngestionProperties;
import com.evanp.f1.ingestion.normalize.CoordinateNormalizer;
import com.evanp.f1.ingestion.normalize.NormalizationResult;
import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1LocationResponse;
import com.evanp.f1.ingestion.openf1.OpenF1SessionResponse;
import com.evanp.f1.persistence.session.RaceSessionEntity;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    private static final long SESSION_KEY = 9161L;
    private static final Instant TIMESTAMP = Instant.parse("2024-03-02T15:00:00Z");
    private static final Instant NOW = Instant.parse("2024-03-02T16:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OpenF1Client openF1Client;

    @Mock
    private CoordinateNormalizer coordinateNormalizer;

    @Mock
    private PositionStore positionStore;

    @Mock
    private RaceSessionRepository raceSessionRepository;

    @Mock
    private IngestionStatusService ingestionStatusService;

    @Mock
    private SessionKeyResolver sessionKeyResolver;

    private IngestionService ingestionService;
    private SessionMetadataSync sessionMetadataSync;

    @BeforeEach
    void setUp() {
        sessionMetadataSync =
                new SessionMetadataSync(openF1Client, raceSessionRepository, sessionKeyResolver, FIXED_CLOCK);
        ingestionService = new IngestionService(
                openF1Client,
                coordinateNormalizer,
                positionStore,
                new IngestionProperties(true, String.valueOf(SESSION_KEY)),
                FIXED_CLOCK,
                sessionMetadataSync,
                ingestionStatusService,
                sessionKeyResolver,
                raceSessionRepository);
    }

    private void stubNumericSessionKey() {
        when(sessionKeyResolver.resolveNumericKey(String.valueOf(SESSION_KEY))).thenReturn(SESSION_KEY);
    }

    private RaceSessionEntity cachedSessionRow() {
        RaceSessionEntity row = RaceSessionEntity.newInstance();
        row.setSessionKey(SESSION_KEY);
        row.setDateStart(TIMESTAMP);
        return row;
    }

    @Test
    void pollOnce_fetchesNormalizesAndSaves() {
        stubNumericSessionKey();
        OpenF1LocationResponse sample = new OpenF1LocationResponse(TIMESTAMP, 44, 1219L, SESSION_KEY, 1.0, 2.0, 3.0);
        NormalizedPosition normalized = new NormalizedPosition(44, SESSION_KEY, TIMESTAMP, 0.1, 0.2, 0.3);
        SessionBounds updatedBounds = SessionBounds.empty().expand(1.0, 2.0, 3.0);

        when(openF1Client.fetchSession(String.valueOf(SESSION_KEY)))
                .thenReturn(Optional.of(new OpenF1SessionResponse(
                        SESSION_KEY, 1219L, "Race", "Bahrain", TIMESTAMP, TIMESTAMP.plusSeconds(3600))));
        when(raceSessionRepository.findById(SESSION_KEY))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(cachedSessionRow()));
        when(positionStore.getPollCursor(SESSION_KEY)).thenReturn(Optional.empty());
        when(openF1Client.fetchLocations(
                        eq(String.valueOf(SESSION_KEY)), eq(Optional.of(TIMESTAMP)), any(Optional.class)))
                .thenReturn(List.of(sample));
        when(positionStore.getBounds(SESSION_KEY)).thenReturn(Optional.empty());
        when(coordinateNormalizer.normalize(List.of(sample), SessionBounds.empty()))
                .thenReturn(new NormalizationResult(List.of(normalized), updatedBounds));

        ingestionService.pollOnce();

        ArgumentCaptor<RaceSessionEntity> sessionCaptor = ArgumentCaptor.forClass(RaceSessionEntity.class);
        verify(raceSessionRepository).save(sessionCaptor.capture());
        RaceSessionEntity savedSession = sessionCaptor.getValue();
        assertEquals(SESSION_KEY, savedSession.getSessionKey());
        assertEquals(1219L, savedSession.getMeetingKey());
        assertEquals("Race", savedSession.getSessionName());
        assertEquals("Bahrain", savedSession.getCircuitName());
        assertEquals(TIMESTAMP, savedSession.getDateStart());
        assertEquals(NOW, savedSession.getCreatedAt());

        verify(positionStore).savePositions(SESSION_KEY, List.of(normalized));
        verify(positionStore).appendHistory(SESSION_KEY, List.of(normalized));
        verify(positionStore).saveBounds(SESSION_KEY, updatedBounds);
        verify(positionStore).savePollCursor(SESSION_KEY, TIMESTAMP);

        Optional<Instant> until = captureUntilArgument();
        assertEquals(Optional.of(TIMESTAMP.plus(IngestionConstants.POLL_WINDOW)), until);
    }

    @Test
    void pollOnce_whenBoundsExpand_renormalizesExistingPositions() {
        stubNumericSessionKey();
        CoordinateNormalizer realNormalizer = new CoordinateNormalizer();
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.empty());
        IngestionService service = new IngestionService(
                openF1Client,
                realNormalizer,
                positionStore,
                new IngestionProperties(true, String.valueOf(SESSION_KEY)),
                FIXED_CLOCK,
                sessionMetadataSync,
                ingestionStatusService,
                sessionKeyResolver,
                raceSessionRepository);

        Instant later = TIMESTAMP.plusSeconds(1);
        SessionBounds oldBounds = SessionBounds.empty().expand(0.0, 0.0, 0.0).expand(10.0, 0.0, 0.0);
        NormalizedPosition existing = new NormalizedPosition(1, SESSION_KEY, TIMESTAMP, 1.0, 0.0, 0.0);
        OpenF1LocationResponse newSample =
                new OpenF1LocationResponse(later, 2, 1219L, SESSION_KEY, 20.0, 0.0, 0.0);

        when(openF1Client.fetchSession(String.valueOf(SESSION_KEY)))
                .thenReturn(Optional.of(new OpenF1SessionResponse(
                        SESSION_KEY, 1219L, "Race", "Bahrain", TIMESTAMP, TIMESTAMP.plusSeconds(3600))));
        when(positionStore.getPollCursor(SESSION_KEY)).thenReturn(Optional.of(TIMESTAMP));
        when(openF1Client.fetchLocations(
                        eq(String.valueOf(SESSION_KEY)), eq(Optional.of(TIMESTAMP)), any(Optional.class)))
                .thenReturn(List.of(newSample));
        when(positionStore.getBounds(SESSION_KEY)).thenReturn(Optional.of(oldBounds));
        when(positionStore.getAllPositions(SESSION_KEY)).thenReturn(List.of(existing));

        service.pollOnce();

        ArgumentCaptor<List<NormalizedPosition>> captor = ArgumentCaptor.forClass(List.class);
        verify(positionStore).savePositions(eq(SESSION_KEY), captor.capture());

        List<NormalizedPosition> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(0.0, saved.stream().filter(p -> p.driverNumber() == 1).findFirst().orElseThrow().x());
        assertEquals(1.0, saved.stream().filter(p -> p.driverNumber() == 2).findFirst().orElseThrow().x());

        Optional<Instant> until = captureUntilArgument();
        assertEquals(Optional.of(TIMESTAMP.plus(IngestionConstants.POLL_WINDOW)), until);
    }

    @Test
    void pollOnce_whenNoLocations_doesNotSave() {
        stubNumericSessionKey();
        when(openF1Client.fetchSession(String.valueOf(SESSION_KEY)))
                .thenReturn(Optional.of(new OpenF1SessionResponse(
                        SESSION_KEY, 1219L, "Race", "Bahrain", TIMESTAMP, TIMESTAMP.plusSeconds(3600))));
        when(raceSessionRepository.findById(SESSION_KEY))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(cachedSessionRow()));
        when(positionStore.getPollCursor(SESSION_KEY)).thenReturn(Optional.empty());
        when(openF1Client.fetchLocations(
                        eq(String.valueOf(SESSION_KEY)), eq(Optional.of(TIMESTAMP)), any(Optional.class)))
                .thenReturn(List.of());

        ingestionService.pollOnce();

        verify(positionStore, never()).savePositions(eq(SESSION_KEY), any());
        verify(positionStore, never()).saveBounds(eq(SESSION_KEY), any());

        Optional<Instant> until = captureUntilArgument();
        Instant expectedUntil = TIMESTAMP.plus(IngestionConstants.POLL_WINDOW);
        assertEquals(Optional.of(expectedUntil), until);
        verify(positionStore).savePollCursor(SESSION_KEY, expectedUntil);
    }

    @Test
    void pollOnce_usesDbDateStartWhenPollCursorMissing() {
        stubNumericSessionKey();
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.of(cachedSessionRow()));
        when(positionStore.getPollCursor(SESSION_KEY)).thenReturn(Optional.empty());
        when(openF1Client.fetchLocations(
                        eq(String.valueOf(SESSION_KEY)), eq(Optional.of(TIMESTAMP)), any(Optional.class)))
                .thenReturn(List.of());

        ingestionService.pollOnce();

        verify(openF1Client, never()).fetchSession(any());
    }

    @Test
    void pollOnce_nonNumericKey_looksUpSessionOnceInRepository() {
        when(sessionKeyResolver.resolveNumericKey("latest")).thenReturn(SESSION_KEY);
        RaceSessionEntity existing = RaceSessionEntity.newInstance();
        existing.setSessionKey(SESSION_KEY);
        existing.setDateStart(TIMESTAMP);
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.of(existing));
        when(openF1Client.fetchSession("latest"))
                .thenReturn(Optional.of(new OpenF1SessionResponse(
                        SESSION_KEY, 1219L, "Race", "Bahrain", TIMESTAMP, TIMESTAMP.plusSeconds(3600))));
        when(positionStore.getPollCursor(SESSION_KEY)).thenReturn(Optional.of(TIMESTAMP));
        when(openF1Client.fetchLocations(eq("latest"), eq(Optional.of(TIMESTAMP)), any(Optional.class)))
                .thenReturn(List.of());

        IngestionService service = new IngestionService(
                openF1Client,
                coordinateNormalizer,
                positionStore,
                new IngestionProperties(true, "latest"),
                FIXED_CLOCK,
                sessionMetadataSync,
                ingestionStatusService,
                sessionKeyResolver,
                raceSessionRepository);
        service.pollOnce();

        verify(raceSessionRepository, times(1)).findById(SESSION_KEY);
        verify(openF1Client, never()).fetchSession(eq(String.valueOf(SESSION_KEY)));
    }

    @SuppressWarnings("unchecked")
    private Optional<Instant> captureUntilArgument() {
        ArgumentCaptor<Optional<Instant>> untilCaptor = ArgumentCaptor.forClass(Optional.class);
        verify(openF1Client)
                .fetchLocations(eq(String.valueOf(SESSION_KEY)), eq(Optional.of(TIMESTAMP)), untilCaptor.capture());
        return untilCaptor.getValue();
    }
}
