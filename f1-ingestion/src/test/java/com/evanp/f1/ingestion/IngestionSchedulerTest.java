package com.evanp.f1.ingestion;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.ingestion.openf1.OpenF1Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionSchedulerTest {

    @Mock
    private IngestionService ingestionService;

    @Mock
    private StintIngestionService stintIngestionService;

    @Mock
    private OpenF1Client openF1Client;

    private IngestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new IngestionScheduler(ingestionService, stintIngestionService, openF1Client);
    }

    @Test
    void tick_skipsWhenRateLimited() {
        when(openF1Client.isRateLimited()).thenReturn(true);

        scheduler.tick();

        verify(ingestionService, never()).pollOnce();
    }

    @Test
    void tick_pollsWhenNotRateLimited() {
        when(openF1Client.isRateLimited()).thenReturn(false);

        scheduler.tick();

        verify(ingestionService).pollOnce();
    }

    @Test
    void pollStints_skipsWhenRateLimited() {
        when(openF1Client.isRateLimited()).thenReturn(true);

        scheduler.pollStints();

        verify(stintIngestionService, never()).pollOnce();
    }

    @Test
    void pollStints_pollsWhenNotRateLimited() {
        when(openF1Client.isRateLimited()).thenReturn(false);

        scheduler.pollStints();

        verify(stintIngestionService).pollOnce();
    }
}
