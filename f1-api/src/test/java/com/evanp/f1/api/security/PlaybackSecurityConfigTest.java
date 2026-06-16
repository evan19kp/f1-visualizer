package com.evanp.f1.api.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evanp.f1.api.TestApplication;
import com.evanp.f1.api.TestInfrastructureConfiguration;
import com.evanp.f1.api.playback.PlaybackService;
import com.evanp.f1.api.playback.PlaybackState;
import com.evanp.f1.api.rest.PlaybackController;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {TestApplication.class, PlaybackController.class})
@Import(TestInfrastructureConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.dev.enabled=true")
class PlaybackSecurityConfigTest {

    private static final PlaybackState STUB_STATE =
            new PlaybackState(
                    Instant.parse("2024-03-02T14:00:00Z"),
                    Instant.parse("2024-03-02T15:00:00Z"),
                    Instant.parse("2024-03-02T14:30:00Z"),
                    PlaybackState.State.PAUSED,
                    1.0,
                    true);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PlaybackService playbackService;

    @BeforeEach
    void stubPlayback() {
        when(playbackService.play(anyLong(), anyDouble())).thenReturn(STUB_STATE);
        when(playbackService.pause(anyLong())).thenReturn(STUB_STATE);
        when(playbackService.seek(anyLong(), any(Instant.class))).thenReturn(STUB_STATE);
    }

    @Test
    void playbackPlay_isPermittedWithoutAuthWhenDevModeEnabled() throws Exception {
        mockMvc.perform(post("/api/sessions/9161/playback/play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speed\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void playbackPause_isPermittedWithoutAuthWhenDevModeEnabled() throws Exception {
        mockMvc.perform(post("/api/sessions/9161/playback/pause")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void playbackSeek_isPermittedWithoutAuthWhenDevModeEnabled() throws Exception {
        mockMvc.perform(post("/api/sessions/9161/playback/seek")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instant\":\"2024-03-02T15:00:00Z\"}"))
                .andExpect(status().isOk());
    }
}
