package com.evanp.f1.api.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.api.dto.SessionResetResponse;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.ingestion.IngestionService;
import com.evanp.f1.ingestion.SessionKeyResolver;
import com.evanp.f1.ingestion.config.IngestionProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionResetServiceTest {

    private static final long SESSION_KEY = 9161L;

    @Mock
    private PositionStore positionStore;

    @Mock
    private IngestionService ingestionService;

    @Mock
    private SessionKeyResolver sessionKeyResolver;

    private SessionResetService sessionResetService;

    @Test
    void reset_clearsSessionAndTriggersReingestWhenKeyMatches() {
        List<String> cleared = List.of("f1:session:9161:positions");
        when(positionStore.clearSession(SESSION_KEY)).thenReturn(cleared);
        when(sessionKeyResolver.resolveNumericKey("9161")).thenReturn(SESSION_KEY);
        sessionResetService = new SessionResetService(
                positionStore, new IngestionProperties(true, "9161", false, false), ingestionService, sessionKeyResolver);

        SessionResetResponse response = sessionResetService.reset(SESSION_KEY);

        assertThat(response.clearedKeys()).isEqualTo(cleared);
        assertThat(response.reingestTriggered()).isTrue();
        verify(ingestionService).pollOnce();
    }

    @Test
    void reset_triggersReingestWhenConfigKeyIsLatestAndResolvesToSession() {
        when(positionStore.clearSession(SESSION_KEY)).thenReturn(List.of());
        when(sessionKeyResolver.resolveNumericKey("latest")).thenReturn(SESSION_KEY);
        sessionResetService = new SessionResetService(
                positionStore, new IngestionProperties(true, "latest", false, false), ingestionService, sessionKeyResolver);

        SessionResetResponse response = sessionResetService.reset(SESSION_KEY);

        assertThat(response.reingestTriggered()).isTrue();
        verify(ingestionService).pollOnce();
    }

    @Test
    void reset_skipsReingestWhenIngestionDisabled() {
        when(positionStore.clearSession(SESSION_KEY)).thenReturn(List.of());
        sessionResetService = new SessionResetService(
                positionStore, new IngestionProperties(false, "9161", false, false), ingestionService, sessionKeyResolver);

        SessionResetResponse response = sessionResetService.reset(SESSION_KEY);

        assertThat(response.reingestTriggered()).isFalse();
        verify(ingestionService, never()).pollOnce();
    }

    @Test
    void reset_skipsReingestWhenSessionKeyDoesNotMatch() {
        when(positionStore.clearSession(SESSION_KEY)).thenReturn(List.of());
        when(sessionKeyResolver.resolveNumericKey("7953")).thenReturn(7953L);
        sessionResetService = new SessionResetService(
                positionStore, new IngestionProperties(true, "7953", false, false), ingestionService, sessionKeyResolver);

        SessionResetResponse response = sessionResetService.reset(SESSION_KEY);

        assertThat(response.reingestTriggered()).isFalse();
        verify(ingestionService, never()).pollOnce();
    }

    @Test
    void reset_returnsClearedKeysWhenReingestFails() {
        List<String> cleared = List.of("f1:session:9161:positions");
        when(positionStore.clearSession(SESSION_KEY)).thenReturn(cleared);
        when(sessionKeyResolver.resolveNumericKey("9161")).thenReturn(SESSION_KEY);
        sessionResetService = new SessionResetService(
                positionStore, new IngestionProperties(true, "9161", false, false), ingestionService, sessionKeyResolver);
        doThrow(new RuntimeException("OpenF1 unavailable")).when(ingestionService).pollOnce();

        SessionResetResponse response = sessionResetService.reset(SESSION_KEY);

        assertThat(response.clearedKeys()).isEqualTo(cleared);
        assertThat(response.reingestTriggered()).isFalse();
        verify(ingestionService).pollOnce();
    }
}
