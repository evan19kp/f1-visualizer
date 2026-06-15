package com.evanp.f1.api.dev;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evanp.f1.api.dto.SessionResetResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DevSessionControllerTest {

    private static final long SESSION_KEY = 9161L;

    @Mock
    private SessionResetService sessionResetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new com.evanp.f1.api.rest.DevSessionController(sessionResetService))
                .build();
    }

    @Test
    void resetSession_returnsSummary() throws Exception {
        List<String> cleared =
                List.of("f1:session:9161:positions", "f1:session:9161:bounds", "f1:session:9161:poll_cursor");
        when(sessionResetService.reset(SESSION_KEY)).thenReturn(new SessionResetResponse(cleared, true));

        mockMvc.perform(post("/api/dev/sessions/{sessionKey}/reset", SESSION_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reingestTriggered").value(true))
                .andExpect(jsonPath("$.clearedKeys.length()").value(3));

        verify(sessionResetService).reset(SESSION_KEY);
    }
}
