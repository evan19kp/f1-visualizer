package com.evanp.f1.api.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionBounds;
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
class PositionControllerTest {

    private static final long SESSION_KEY = 9161L;
    private static final Instant TIMESTAMP = Instant.parse("2024-03-02T15:00:00Z");

    @Mock
    private PositionStore positionStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PositionController(positionStore)).build();
    }

    @Test
    void getPositions_appliesLimitAndOffset() throws Exception {
        NormalizedPosition first = new NormalizedPosition(1, SESSION_KEY, TIMESTAMP, 0.1, 0.2, 0.3);
        NormalizedPosition second = new NormalizedPosition(44, SESSION_KEY, TIMESTAMP, 0.4, 0.5, 0.6);
        NormalizedPosition third = new NormalizedPosition(63, SESSION_KEY, TIMESTAMP, 0.7, 0.8, 0.9);
        when(positionStore.getAllPositions(SESSION_KEY)).thenReturn(List.of(first, second, third));

        mockMvc.perform(get("/api/sessions/{sessionKey}/positions", SESSION_KEY)
                        .param("limit", "1")
                        .param("offset", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].driverNumber").value(44));
    }

    @Test
    void getPositions_rejectsInvalidPagingParams() throws Exception {
        mockMvc.perform(get("/api/sessions/{sessionKey}/positions", SESSION_KEY)
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPositions_returnsJsonArray() throws Exception {
        NormalizedPosition position =
                new NormalizedPosition(44, SESSION_KEY, TIMESTAMP, 0.1, 0.2, 0.3);
        when(positionStore.getAllPositions(SESSION_KEY)).thenReturn(List.of(position));

        mockMvc.perform(get("/api/sessions/{sessionKey}/positions", SESSION_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].driverNumber").value(44))
                .andExpect(jsonPath("$[0].sessionKey").value(SESSION_KEY))
                .andExpect(jsonPath("$[0].timestamp").value(TIMESTAMP.toString()))
                .andExpect(jsonPath("$[0].x").value(0.1))
                .andExpect(jsonPath("$[0].y").value(0.2))
                .andExpect(jsonPath("$[0].z").value(0.3));
    }

    @Test
    void getDriverPosition_returnsLatestPosition() throws Exception {
        NormalizedPosition position =
                new NormalizedPosition(44, SESSION_KEY, TIMESTAMP, 0.4, 0.5, 0.6);
        when(positionStore.getLatest(SESSION_KEY, 44)).thenReturn(Optional.of(position));

        mockMvc.perform(get("/api/sessions/{sessionKey}/positions/{driverNumber}", SESSION_KEY, 44))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverNumber").value(44))
                .andExpect(jsonPath("$.x").value(0.4));
    }

    @Test
    void getDriverPosition_returnsNotFoundWhenMissing() throws Exception {
        when(positionStore.getLatest(SESSION_KEY, 99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/{sessionKey}/positions/{driverNumber}", SESSION_KEY, 99))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBounds_returnsSessionBounds() throws Exception {
        SessionBounds bounds = SessionBounds.empty().expand(1.0, 2.0, 3.0);
        when(positionStore.getBounds(SESSION_KEY)).thenReturn(Optional.of(bounds));

        mockMvc.perform(get("/api/sessions/{sessionKey}/bounds", SESSION_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minX").value(1.0))
                .andExpect(jsonPath("$.maxX").value(1.0))
                .andExpect(jsonPath("$.minY").value(2.0))
                .andExpect(jsonPath("$.maxY").value(2.0))
                .andExpect(jsonPath("$.minZ").value(3.0))
                .andExpect(jsonPath("$.maxZ").value(3.0));
    }

    @Test
    void getBounds_returnsNotFoundWhenMissing() throws Exception {
        when(positionStore.getBounds(SESSION_KEY)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/{sessionKey}/bounds", SESSION_KEY))
                .andExpect(status().isNotFound());
    }
}
