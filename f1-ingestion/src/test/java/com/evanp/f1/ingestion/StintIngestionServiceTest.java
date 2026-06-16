package com.evanp.f1.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.core.stint.StintSnapshot;
import com.evanp.f1.core.stint.StintStore;
import com.evanp.f1.ingestion.config.IngestionProperties;
import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1StintResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StintIngestionServiceTest {

    private static final long SESSION_KEY = 9161L;

    @Mock
    private OpenF1Client openF1Client;

    @Mock
    private StintStore stintStore;

    @Mock
    private SessionKeyResolver sessionKeyResolver;

    private StintIngestionService stintIngestionService;

    @BeforeEach
    void setUp() {
        stintIngestionService = new StintIngestionService(
                openF1Client,
                stintStore,
                new IngestionProperties(true, String.valueOf(SESSION_KEY), false, false),
                sessionKeyResolver);
    }

    @Test
    void pollOnce_fetchesStintsAndSavesLatestPerDriver() {
        when(sessionKeyResolver.resolveNumericKey(String.valueOf(SESSION_KEY))).thenReturn(SESSION_KEY);
        OpenF1StintResponse older =
                new OpenF1StintResponse("SOFT", 44, 20, 1, 1219L, SESSION_KEY, 1, 0);
        OpenF1StintResponse latest =
                new OpenF1StintResponse("MEDIUM", 44, null, 21, 1219L, SESSION_KEY, 2, 0);
        OpenF1StintResponse otherDriver =
                new OpenF1StintResponse("HARD", 1, 30, 1, 1219L, SESSION_KEY, 1, 3);

        when(openF1Client.fetchStints(String.valueOf(SESSION_KEY), Optional.empty()))
                .thenReturn(List.of(older, latest, otherDriver));

        stintIngestionService.pollOnce();

        ArgumentCaptor<List<StintSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(stintStore).save(eq(SESSION_KEY), captor.capture());

        List<StintSnapshot> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(
                "MEDIUM",
                saved.stream()
                        .filter(s -> s.driverNumber() == 44)
                        .findFirst()
                        .orElseThrow()
                        .compound());
    }

    @Test
    void pickLatestPerDriver_prefersHigherStintNumber() {
        OpenF1StintResponse first =
                new OpenF1StintResponse("SOFT", 16, 10, 1, 1219L, SESSION_KEY, 1, 0);
        OpenF1StintResponse third =
                new OpenF1StintResponse("HARD", 16, null, 30, 1219L, SESSION_KEY, 3, 0);
        OpenF1StintResponse second =
                new OpenF1StintResponse("MEDIUM", 16, 29, 11, 1219L, SESSION_KEY, 2, 0);

        List<StintSnapshot> result =
                StintIngestionService.pickLatestPerDriver(List.of(first, second, third), SESSION_KEY);

        assertEquals(1, result.size());
        assertEquals(3, result.getFirst().stintNumber());
        assertEquals("HARD", result.getFirst().compound());
    }
}
