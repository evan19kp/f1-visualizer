package com.evanp.f1.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionBounds;
import com.evanp.f1.ingestion.normalize.CoordinateNormalizer;
import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1LocationResponse;
import com.evanp.f1.ingestion.openf1.OpenF1SessionResponse;
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
class SessionHistoryBackfillServiceTest {

    private static final long SESSION_KEY = 9161L;
    private static final Instant START = Instant.parse("2024-03-02T15:00:00Z");
    private static final Instant T1 = START.plusSeconds(1);
    private static final Instant T2 = START.plusSeconds(2);
    private static final Instant T3 = START.plusSeconds(3);
    private static final Clock CLOCK = Clock.fixed(START.plusSeconds(3600), ZoneOffset.UTC);

    @Mock
    private OpenF1Client openF1Client;

    @Mock
    private PositionStore positionStore;

    @Mock
    private SessionKeyResolver sessionKeyResolver;

    private SessionHistoryBackfillService backfillService;

    @BeforeEach
    void setUp() {
        backfillService = new SessionHistoryBackfillService(
                openF1Client,
                new CoordinateNormalizer(),
                positionStore,
                sessionKeyResolver,
                CLOCK);
    }

    @Test
    void backfill_appendsAllTimestampFramesNotOnePerDriver() {
        when(sessionKeyResolver.resolveNumericKey("9161")).thenReturn(SESSION_KEY);
        when(openF1Client.fetchSession("9161"))
                .thenReturn(Optional.of(new OpenF1SessionResponse(
                        SESSION_KEY, 1L, "Qualifying", "Singapore", START, T3)));
        when(positionStore.getBounds(SESSION_KEY)).thenReturn(Optional.empty());
        when(positionStore.getHistoryTimeRange(SESSION_KEY)).thenReturn(Optional.empty());

        List<OpenF1LocationResponse> samples = List.of(
                new OpenF1LocationResponse(T1, 44, SESSION_KEY, 1L, 1.0, 2.0, 3.0),
                new OpenF1LocationResponse(T1, 55, SESSION_KEY, 1L, 4.0, 5.0, 6.0),
                new OpenF1LocationResponse(T2, 44, SESSION_KEY, 1L, 1.1, 2.1, 3.1),
                new OpenF1LocationResponse(T3, 55, SESSION_KEY, 1L, 4.1, 5.1, 6.1));

        when(openF1Client.fetchLocations(eq("9161"), any(), any())).thenReturn(samples);

        backfillService.backfill("9161");

        ArgumentCaptor<List<NormalizedPosition>> captor = ArgumentCaptor.forClass(List.class);
        verify(positionStore).appendHistory(eq(SESSION_KEY), captor.capture());
        List<NormalizedPosition> appended = captor.getValue();
        assertEquals(4, appended.size());
        assertTrue(appended.stream().map(NormalizedPosition::timestamp).distinct().count() == 3);
    }
}
