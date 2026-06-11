package com.evanp.f1.api.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evanp.f1.ai.insight.InsightStore;
import com.evanp.f1.ai.insight.RaceInsight;
import com.evanp.f1.core.event.RaceEventType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InsightControllerTest {

    private static final long SESSION_KEY = 9161L;
    private static final Instant TIMESTAMP = Instant.parse("2024-03-02T15:10:00Z");

    @Mock
    private InsightStore insightStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InsightController(insightStore)).build();
    }

    @Test
    void getInsights_returnsInsightsFromStore() throws Exception {
        RaceInsight insight = new RaceInsight(
                SESSION_KEY, TIMESTAMP, RaceEventType.SAFETY_CAR, "Safety car deployed — box now.");
        when(insightStore.getRecent(SESSION_KEY, 10)).thenReturn(List.of(insight));

        mockMvc.perform(get("/api/sessions/{sessionKey}/insights", SESSION_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionKey").value(SESSION_KEY))
                .andExpect(jsonPath("$[0].eventType").value("SAFETY_CAR"))
                .andExpect(jsonPath("$[0].commentary").value("Safety car deployed — box now."))
                .andExpect(jsonPath("$[0].timestamp").value(TIMESTAMP.toString()));
    }

    @Test
    void getInsights_returnsEmptyListWhenNoInsights() throws Exception {
        when(insightStore.getRecent(SESSION_KEY, 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/sessions/{sessionKey}/insights", SESSION_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
