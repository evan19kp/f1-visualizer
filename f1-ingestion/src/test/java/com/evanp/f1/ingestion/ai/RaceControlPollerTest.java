package com.evanp.f1.ingestion.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.ai.service.RaceEngineerService;
import com.evanp.f1.core.event.RaceEvent;
import com.evanp.f1.core.event.RaceEventType;
import com.evanp.f1.ingestion.config.IngestionProperties;
import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1RaceControlResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RaceControlPollerTest {

    private static final long SESSION_KEY = 9161L;
    private static final Instant TIMESTAMP = Instant.parse("2024-03-02T15:10:00Z");

    @Mock
    private OpenF1Client openF1Client;

    @Mock
    private RaceEngineerService raceEngineerService;

    private RaceControlPoller poller;

    @BeforeEach
    void setUp() {
        RaceControlEventDetector detector = new RaceControlEventDetector();
        IngestionProperties properties = new IngestionProperties(false, String.valueOf(SESSION_KEY));
        poller = new RaceControlPoller(openF1Client, detector, raceEngineerService, properties);
    }

    @Test
    void poll_safetyCarMessage_triggersCommentary() {
        OpenF1RaceControlResponse message = new OpenF1RaceControlResponse(
                TIMESTAMP,
                "SafetyCar",
                null,
                null,
                12,
                1219L,
                "SAFETY CAR DEPLOYED",
                "Track",
                null,
                SESSION_KEY);
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), any())).thenReturn(List.of(message));

        poller.poll();

        ArgumentCaptor<RaceEvent> captor = ArgumentCaptor.forClass(RaceEvent.class);
        verify(raceEngineerService).generateCommentary(captor.capture());
        RaceEvent event = captor.getValue();
        assertEquals(RaceEventType.SAFETY_CAR, event.type());
        assertEquals("SAFETY CAR DEPLOYED", event.summary());
    }

    @Test
    void poll_unrelatedMessage_doesNotTriggerCommentary() {
        OpenF1RaceControlResponse message = new OpenF1RaceControlResponse(
                TIMESTAMP,
                "Flag",
                "GREEN",
                null,
                12,
                1219L,
                "TRACK CLEAR",
                "Track",
                null,
                SESSION_KEY);
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), any())).thenReturn(List.of(message));

        poller.poll();

        verify(raceEngineerService, never()).generateCommentary(any());
    }
}
