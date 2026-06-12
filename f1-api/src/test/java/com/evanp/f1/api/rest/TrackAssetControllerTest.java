package com.evanp.f1.api.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evanp.f1.persistence.s3.TrackAssetService;
import com.evanp.f1.persistence.session.RaceSessionEntity;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TrackAssetControllerTest {

    private static final long SESSION_KEY = 9161L;

    @Mock
    private RaceSessionRepository raceSessionRepository;

    @Mock
    private TrackAssetService trackAssetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TrackAssetController(raceSessionRepository, trackAssetService))
                .build();
    }

    @Test
    void getTrackAsset_returnsPresignedUrl() throws Exception {
        RaceSessionEntity session = mock(RaceSessionEntity.class);
        when(session.getCircuitName()).thenReturn("Bahrain");
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.of(session));
        when(trackAssetService.getPresignedTrackUrl("bahrain"))
                .thenReturn(Optional.of(new URL("https://s3.example.com/tracks/bahrain.glb?X-Amz-Signature=abc")));

        mockMvc.perform(get("/api/sessions/{sessionKey}/track-asset", SESSION_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circuitSlug").value("bahrain"))
                .andExpect(jsonPath("$.url").value("https://s3.example.com/tracks/bahrain.glb?X-Amz-Signature=abc"));
    }

    @Test
    void getTrackAsset_returnsNotFoundWhenSessionMissing() throws Exception {
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/{sessionKey}/track-asset", SESSION_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTrackAsset_returnsNotFoundWhenObjectMissing() throws Exception {
        RaceSessionEntity session = mock(RaceSessionEntity.class);
        when(session.getCircuitName()).thenReturn("Bahrain");
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.of(session));
        when(trackAssetService.getPresignedTrackUrl(eq("bahrain"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/{sessionKey}/track-asset", SESSION_KEY))
                .andExpect(status().isNotFound());
    }
}
