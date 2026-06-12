package com.evanp.f1.api.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evanp.f1.core.stint.StintSnapshot;
import com.evanp.f1.core.stint.StintStore;
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
class StintControllerTest {

    private static final long SESSION_KEY = 9161L;

    @Mock
    private StintStore stintStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StintController(stintStore)).build();
    }

    @Test
    void getStints_returnsAllStints() throws Exception {
        StintSnapshot first = new StintSnapshot(1, SESSION_KEY, "SOFT", 1, 1, 20, 0);
        StintSnapshot second = new StintSnapshot(44, SESSION_KEY, "MEDIUM", 2, 21, null, 0);
        when(stintStore.getAll(SESSION_KEY)).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/sessions/{sessionKey}/stints", SESSION_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].driverNumber").value(1))
                .andExpect(jsonPath("$[0].compound").value("SOFT"))
                .andExpect(jsonPath("$[1].driverNumber").value(44))
                .andExpect(jsonPath("$[1].compound").value("MEDIUM"));
    }

    @Test
    void getDriverStint_returnsLatestStint() throws Exception {
        StintSnapshot stint = new StintSnapshot(44, SESSION_KEY, "HARD", 3, 40, null, 0);
        when(stintStore.getLatest(SESSION_KEY, 44)).thenReturn(Optional.of(stint));

        mockMvc.perform(get("/api/sessions/{sessionKey}/stints/{driverNumber}", SESSION_KEY, 44))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverNumber").value(44))
                .andExpect(jsonPath("$.compound").value("HARD"))
                .andExpect(jsonPath("$.stintNumber").value(3));
    }

    @Test
    void getDriverStint_returnsNotFoundWhenMissing() throws Exception {
        when(stintStore.getLatest(SESSION_KEY, 99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/{sessionKey}/stints/{driverNumber}", SESSION_KEY, 99))
                .andExpect(status().isNotFound());
    }
}
