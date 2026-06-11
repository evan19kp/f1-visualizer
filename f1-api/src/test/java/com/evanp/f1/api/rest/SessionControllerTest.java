package com.evanp.f1.api.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evanp.f1.persistence.session.RaceSessionEntity;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    private static final long SESSION_KEY = 9161L;
    private static final Instant DATE_START = Instant.parse("2024-03-02T14:00:00Z");

    @Mock
    private RaceSessionRepository raceSessionRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SessionController(raceSessionRepository)).build();
    }

    @Test
    void listSessions_returnsEmptyListWhenNoSessions() throws Exception {
        when(raceSessionRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listSessions_returnsSessions() throws Exception {
        RaceSessionEntity session = sampleSession();
        when(raceSessionRepository.findAll()).thenReturn(List.of(session));

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionKey").value(SESSION_KEY))
                .andExpect(jsonPath("$[0].meetingKey").value(1219))
                .andExpect(jsonPath("$[0].sessionName").value("Race"))
                .andExpect(jsonPath("$[0].circuitName").value("Bahrain"))
                .andExpect(jsonPath("$[0].dateStart").value(DATE_START.toString()));
    }

    @Test
    void getSession_returnsSession() throws Exception {
        RaceSessionEntity session = sampleSession();
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/sessions/{sessionKey}", SESSION_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionKey").value(SESSION_KEY))
                .andExpect(jsonPath("$.circuitName").value("Bahrain"));
    }

    @Test
    void getSession_returnsNotFoundWhenMissing() throws Exception {
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/{sessionKey}", SESSION_KEY))
                .andExpect(status().isNotFound());
    }

    private static RaceSessionEntity sampleSession() {
        RaceSessionEntity entity = mock(RaceSessionEntity.class);
        when(entity.getSessionKey()).thenReturn(SESSION_KEY);
        when(entity.getMeetingKey()).thenReturn(1219L);
        when(entity.getSessionName()).thenReturn("Race");
        when(entity.getCircuitName()).thenReturn("Bahrain");
        when(entity.getDateStart()).thenReturn(DATE_START);
        return entity;
    }
}
