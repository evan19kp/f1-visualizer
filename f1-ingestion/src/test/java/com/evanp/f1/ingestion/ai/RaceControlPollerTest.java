package com.evanp.f1.ingestion.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.ai.service.RaceEngineerService;
import com.evanp.f1.core.event.RaceEvent;
import com.evanp.f1.core.event.RaceEventType;
import com.evanp.f1.ingestion.config.IngestionProperties;
import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1RaceControlResponse;
import com.evanp.f1.ingestion.openf1.OpenF1SessionResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private static final Instant LATER_TIMESTAMP = Instant.parse("2024-03-02T15:11:00Z");

    @Mock
    private OpenF1Client openF1Client;

    @Mock
    private RaceEngineerService raceEngineerService;

    private RaceControlPoller poller;

    @BeforeEach
    void setUp() {
        RaceControlEventDetector detector = new RaceControlEventDetector();
        IngestionProperties properties = new IngestionProperties(false, String.valueOf(SESSION_KEY), false, false);
        poller = new RaceControlPoller(openF1Client, detector, raceEngineerService, properties);
    }

    @Test
    void poll_safetyCarMessage_triggersCommentary() {
        OpenF1RaceControlResponse message = safetyCarMessage("SAFETY CAR DEPLOYED");
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), any())).thenReturn(List.of(message));

        poller.poll();

        ArgumentCaptor<RaceEvent> captor = ArgumentCaptor.forClass(RaceEvent.class);
        verify(raceEngineerService).generateCommentary(captor.capture());
        RaceEvent event = captor.getValue();
        assertEquals(RaceEventType.SAFETY_CAR, event.type());
        assertEquals("SAFETY CAR DEPLOYED", event.summary());
    }

    @Test
    void poll_vscMessage_triggersSafetyCarCommentary() {
        OpenF1RaceControlResponse message = safetyCarMessage("VSC DEPLOYED");
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), any())).thenReturn(List.of(message));

        poller.poll();

        ArgumentCaptor<RaceEvent> captor = ArgumentCaptor.forClass(RaceEvent.class);
        verify(raceEngineerService).generateCommentary(captor.capture());
        assertEquals(RaceEventType.SAFETY_CAR, captor.getValue().type());
    }

    @Test
    void poll_pitWindowMessage_triggersPitWindowCommentary() {
        OpenF1RaceControlResponse message = new OpenF1RaceControlResponse(
                TIMESTAMP, "Pit", null, null, 12, 1219L, "PIT WINDOW OPEN", "Track", null, SESSION_KEY);
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), any())).thenReturn(List.of(message));

        poller.poll();

        ArgumentCaptor<RaceEvent> captor = ArgumentCaptor.forClass(RaceEvent.class);
        verify(raceEngineerService).generateCommentary(captor.capture());
        assertEquals(RaceEventType.PIT_WINDOW, captor.getValue().type());
    }

    @Test
    void poll_blankMessage_doesNotTriggerCommentary() {
        OpenF1RaceControlResponse message = new OpenF1RaceControlResponse(
                TIMESTAMP, "Flag", "GREEN", null, 12, 1219L, "  ", "Track", null, SESSION_KEY);
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), any())).thenReturn(List.of(message));

        poller.poll();

        verify(raceEngineerService, never()).generateCommentary(any());
    }

    @Test
    void poll_multipleMessages_triggersCommentaryForEachEvent() {
        OpenF1RaceControlResponse safetyCar = safetyCarMessage("SAFETY CAR DEPLOYED");
        OpenF1RaceControlResponse pitWindow = new OpenF1RaceControlResponse(
                LATER_TIMESTAMP, "Pit", null, null, 13, 1219L, "PIT WINDOW OPEN", "Track", null, SESSION_KEY);
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), any()))
                .thenReturn(List.of(safetyCar, pitWindow));

        poller.poll();

        ArgumentCaptor<RaceEvent> captor = ArgumentCaptor.forClass(RaceEvent.class);
        verify(raceEngineerService, times(2)).generateCommentary(captor.capture());
        assertEquals(RaceEventType.SAFETY_CAR, captor.getAllValues().get(0).type());
        assertEquals(RaceEventType.PIT_WINDOW, captor.getAllValues().get(1).type());
    }

    @Test
    void poll_advancesCursorBetweenPolls() {
        OpenF1RaceControlResponse message = safetyCarMessage("SAFETY CAR DEPLOYED");
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), eq(Optional.empty())))
                .thenReturn(List.of(message));
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), eq(Optional.of(TIMESTAMP))))
                .thenReturn(List.of());

        poller.poll();
        poller.poll();

        verify(openF1Client).fetchRaceControl(String.valueOf(SESSION_KEY), Optional.empty());
        verify(openF1Client).fetchRaceControl(String.valueOf(SESSION_KEY), Optional.of(TIMESTAMP));
    }

    @Test
    void poll_commentaryFailure_doesNotAdvanceCursor() {
        OpenF1RaceControlResponse message = safetyCarMessage("SAFETY CAR DEPLOYED");
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), any())).thenReturn(List.of(message));
        doThrow(new RuntimeException("commentary failed")).when(raceEngineerService).generateCommentary(any());

        poller.poll();
        poller.poll();

        verify(openF1Client, times(2)).fetchRaceControl(eq(String.valueOf(SESSION_KEY)), eq(Optional.empty()));
    }

    @Test
    void poll_unrelatedMessage_doesNotTriggerCommentary() {
        OpenF1RaceControlResponse message = new OpenF1RaceControlResponse(
                TIMESTAMP, "Flag", "GREEN", null, 12, 1219L, "TRACK CLEAR", "Track", null, SESSION_KEY);
        when(openF1Client.fetchRaceControl(eq(String.valueOf(SESSION_KEY)), any())).thenReturn(List.of(message));

        poller.poll();

        verify(raceEngineerService, never()).generateCommentary(any());
    }

    @Test
    void poll_nonNumericConfigKey_cachesSessionLookup() {
        RaceControlEventDetector detector = new RaceControlEventDetector();
        IngestionProperties properties = new IngestionProperties(false, "latest", false, false);
        poller = new RaceControlPoller(openF1Client, detector, raceEngineerService, properties);

        OpenF1SessionResponse session = new OpenF1SessionResponse(
                SESSION_KEY, 1219L, "Race", "Bahrain", TIMESTAMP, null);
        when(openF1Client.fetchSession("latest")).thenReturn(Optional.of(session));
        when(openF1Client.fetchRaceControl(eq("latest"), any())).thenReturn(List.of());

        poller.poll();
        poller.poll();

        verify(openF1Client, times(1)).fetchSession("latest");
    }

    private static OpenF1RaceControlResponse safetyCarMessage(String text) {
        return new OpenF1RaceControlResponse(
                TIMESTAMP, "SafetyCar", null, null, 12, 1219L, text, "Track", null, SESSION_KEY);
    }
}
