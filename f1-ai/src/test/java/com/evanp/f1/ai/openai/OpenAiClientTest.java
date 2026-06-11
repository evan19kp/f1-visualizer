package com.evanp.f1.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.evanp.f1.ai.config.OpenAiProperties;
import com.evanp.f1.core.event.RaceEvent;
import com.evanp.f1.core.event.RaceEventType;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiClientTest {

    private MockRestServiceServer server;
    private OpenAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiClient(builder, new OpenAiProperties("test-key", "gpt-4o-mini", 30));
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void generateCommentary_returnsResponseText() {
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"role":"assistant","content":"Box now for the undercut."}}]}
                        """,
                        MediaType.APPLICATION_JSON));

        RaceEvent event = new RaceEvent(9161L, Instant.parse("2024-03-02T15:00:00Z"), RaceEventType.UNDERCUT, "P1 pits");

        assertEquals("Box now for the undercut.", client.generateCommentary(event).orElseThrow());
    }

    @Test
    void generateCommentary_noOpsWhenApiKeyBlank() {
        OpenAiClient noKeyClient = new OpenAiClient(RestClient.builder(), new OpenAiProperties("", "gpt-4o-mini", 30));
        RaceEvent event = new RaceEvent(9161L, Instant.now(), RaceEventType.OVERTAKE, "P3 passes P4");

        assertTrue(noKeyClient.generateCommentary(event).isEmpty());
    }
}
