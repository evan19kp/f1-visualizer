package com.evanp.f1.ingestion;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionTimeRange;
import com.evanp.f1.ingestion.config.IngestionProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionBootstrapServiceTest {

    private static final long SESSION_KEY = 9161L;
    private static final Instant START = Instant.parse("2024-03-02T15:00:00Z");

    @Mock
    private SessionKeyResolver sessionKeyResolver;

    @Mock
    private PositionStore positionStore;

    @Mock
    private SessionMetadataSync sessionMetadataSync;

    @Mock
    private SessionHistoryBackfillService backfillService;

    @Mock
    private IngestionStatusService ingestionStatusService;

    private SessionBootstrapService sessionBootstrapService;

    @BeforeEach
    void setUp() {
        sessionBootstrapService = new SessionBootstrapService(
                new IngestionProperties(true, String.valueOf(SESSION_KEY), true, false),
                sessionKeyResolver,
                positionStore,
                sessionMetadataSync,
                backfillService,
                ingestionStatusService);
    }

    @Test
    void bootstrapOnStartup_skipsWhenAutoBootstrapDisabled() {
        SessionBootstrapService disabled = new SessionBootstrapService(
                new IngestionProperties(true, String.valueOf(SESSION_KEY), false, false),
                sessionKeyResolver,
                positionStore,
                sessionMetadataSync,
                backfillService,
                ingestionStatusService);

        disabled.bootstrapOnStartup();

        verify(backfillService, never()).backfill(org.mockito.ArgumentMatchers.any(), eq(true));
    }

    @Test
    void bootstrapOnStartup_backfillsWhenHistoryMissing() {
        when(sessionKeyResolver.resolveNumericKey(String.valueOf(SESSION_KEY))).thenReturn(SESSION_KEY);
        when(positionStore.hasHistory(SESSION_KEY)).thenReturn(false);
        when(backfillService.backfill(String.valueOf(SESSION_KEY), true))
                .thenReturn(new SessionHistoryBackfillService.BackfillResult(SESSION_KEY, 10, true, "none"));

        sessionBootstrapService.bootstrapOnStartup();

        InOrder order = inOrder(ingestionStatusService, sessionMetadataSync, backfillService);
        order.verify(ingestionStatusService, timeout(5000)).markBootstrapRunning();
        order.verify(sessionMetadataSync, timeout(5000)).syncIfNeeded(String.valueOf(SESSION_KEY));
        order.verify(backfillService, timeout(5000)).backfill(String.valueOf(SESSION_KEY), true);
        order.verify(ingestionStatusService, timeout(5000)).markBootstrapComplete();
    }

    @Test
    void bootstrapOnStartup_ensuresDisplayPositionsWhenHistoryExists() {
        when(sessionKeyResolver.resolveNumericKey(String.valueOf(SESSION_KEY))).thenReturn(SESSION_KEY);
        when(positionStore.hasHistory(SESSION_KEY)).thenReturn(true);
        when(positionStore.getAllPositions(SESSION_KEY)).thenReturn(List.of());
        when(positionStore.getHistoryTimeRange(SESSION_KEY))
                .thenReturn(Optional.of(new SessionTimeRange(START, START.plusSeconds(3600))));
        NormalizedPosition position = new NormalizedPosition(44, SESSION_KEY, START, 0.1, 0.0, 0.2);
        when(positionStore.getFrameAt(SESSION_KEY, START)).thenReturn(List.of(position));

        sessionBootstrapService.bootstrapOnStartup();

        verify(ingestionStatusService).markBootstrapComplete();
        verify(ingestionStatusService, never()).markBootstrapRunning();
        verify(backfillService, never()).backfill(org.mockito.ArgumentMatchers.any(), eq(true));
        verify(positionStore).savePositions(SESSION_KEY, List.of(position));
    }

    @Test
    void bootstrapOnStartup_usesEndFrameWhenStartFrameEmpty() {
        Instant end = START.plusSeconds(3600);
        when(sessionKeyResolver.resolveNumericKey(String.valueOf(SESSION_KEY))).thenReturn(SESSION_KEY);
        when(positionStore.hasHistory(SESSION_KEY)).thenReturn(true);
        when(positionStore.getAllPositions(SESSION_KEY)).thenReturn(List.of());
        when(positionStore.getHistoryTimeRange(SESSION_KEY))
                .thenReturn(Optional.of(new SessionTimeRange(START, end)));
        NormalizedPosition position = new NormalizedPosition(44, SESSION_KEY, end, 0.1, 0.0, 0.2);
        when(positionStore.getFrameAt(SESSION_KEY, START)).thenReturn(List.of());
        when(positionStore.getFrameAt(SESSION_KEY, end)).thenReturn(List.of(position));

        sessionBootstrapService.bootstrapOnStartup();

        verify(positionStore).savePositions(SESSION_KEY, List.of(position));
    }
}
