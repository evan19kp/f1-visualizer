package com.evanp.f1.api.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evanp.f1.api.TestApplication;
import com.evanp.f1.api.TestInfrastructureConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestApplication.class)
@Import(TestInfrastructureConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void login_isPermittedWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"testpass\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedApiRoute_returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/sessions/9161/insights")).andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorHealth_isPermittedWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void actuatorInfo_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicSessionsListRoute_isPermittedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/sessions")).andExpect(status().isNotFound());
    }

    @Test
    void publicSessionMetadataRoute_isPermittedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/sessions/9161")).andExpect(status().isNotFound());
    }

    @Test
    void publicBoundsRoute_isPermittedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/sessions/9161/bounds")).andExpect(status().isNotFound());
    }

    @Test
    void publicTrackAssetRoute_isPermittedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/sessions/9161/track-asset")).andExpect(status().isNotFound());
    }

    @Test
    void publicPositionsRoute_isPermittedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/sessions/9161/positions")).andExpect(status().isNotFound());
    }
}
