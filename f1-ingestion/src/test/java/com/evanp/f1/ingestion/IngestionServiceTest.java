package com.evanp.f1.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    private static final long SESSION_KEY = 9161L;
    private static final Instant TIMESTAMP = Instant.parse("2024-03-02T15:00:00Z");

    @Mock
    private OpenF1Client openF1Client;

    @Mock
    private CoordinateNormalizer coordinateNormalizer;

    @Mock
    private PositionStore positionStore;

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(
                openF1Client,
                coordinateNormalizer,
                positionStore,
                new IngestionProperties(true, String.valueOf(SESSION_KEY)));
    }

    @Test
    void pollOnce_fetchesNormalizesAndSaves() {
        OpenF1LocationResponse sample = new OpenF1LocationResponse(TIMESTAMP, 44, 1219L, SESSION_KEY, 1.0, 2.0, 3.0);
        NormalizedPosition normalized = new NormalizedPosition(44, SESSION_KEY, TIMESTAMP, 0.1, 0.2, 0.3);
        SessionBounds updatedBounds = SessionBounds.empty().expand(1.0, 2.0, 3.0);

        when(openF1Client.fetchSession(String.valueOf(SESSION_KEY)))
                .thenReturn(Optional.of(new OpenF1SessionResponse(
                        SESSION_KEY, 1219L, "Race", "Bahrain", TIMESTAMP)));
        when(positionStore.getPollCursor(SESSION_KEY)).thenReturn(Optional.empty());
        when(openF1Client.fetchLocations(String.valueOf(SESSION_KEY), Optional.of(TIMESTAMP)))
                .thenReturn(List.of(sample));
        when(positionStore.getBounds(SESSION_KEY)).thenReturn(Optional.empty());
        when(coordinateNormalizer.normalize(List.of(sample), SessionBounds.empty()))
                .thenReturn(new NormalizationResult(List.of(normalized), updatedBounds));

        ingestionService.pollOnce();

        verify(positionStore).savePositions(SESSION_KEY, List.of(normalized));
        verify(positionStore).saveBounds(SESSION_KEY, updatedBounds);
        verify(positionStore).savePollCursor(SESSION_KEY, TIMESTAMP);
    }

    @Test
    void pollOnce_whenNoLocations_doesNotSave() {
        when(openF1Client.fetchSession(String.valueOf(SESSION_KEY)))
                .thenReturn(Optional.of(new OpenF1SessionResponse(
                        SESSION_KEY, 1219L, "Race", "Bahrain", TIMESTAMP)));
        when(positionStore.getPollCursor(SESSION_KEY)).thenReturn(Optional.empty());
        when(openF1Client.fetchLocations(String.valueOf(SESSION_KEY), Optional.of(TIMESTAMP)))
                .thenReturn(List.of());

        ingestionService.pollOnce();

        verify(positionStore, never()).savePositions(eq(SESSION_KEY), any());
        verify(positionStore, never()).saveBounds(eq(SESSION_KEY), any());
        verify(positionStore, never()).savePollCursor(eq(SESSION_KEY), any());
    }
}
