package com.evanp.f1.ingestion.openf1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class OpenF1ClientTest {

    private MockRestServiceServer server;
    private OpenF1Client client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenF1Client(builder, "http://localhost");
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void fetchLocations_returnsParsedResponses() {
        String body =
                """
                [
                  {
                    "date": "2024-03-02T15:00:00+00:00",
                    "driver_number": 44,
                    "meeting_key": 1219,
                    "session_key": 9161,
                    "x": 1.0,
                    "y": 2.0,
                    "z": 3.0
                  }
                ]
                """;

        server.expect(requestTo("http://localhost/location?session_key=9161"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<OpenF1LocationResponse> locations = client.fetchLocations("9161", Optional.empty());

        assertEquals(1, locations.size());
        assertEquals(44, locations.getFirst().driverNumber());
        assertEquals(9161L, locations.getFirst().sessionKey());
        assertEquals(1.0, locations.getFirst().x());
    }

    @Test
    void fetchLocations_appendsDateFilterWhenSincePresent() {
        Instant since = Instant.parse("2024-03-02T14:00:00Z");

        server.expect(requestTo("http://localhost/location?session_key=9161&date%3E=2024-03-02T14:00:00Z"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(client.fetchLocations("9161", Optional.of(since)).isEmpty());
    }

    @Test
    void fetchLocations_onServerError_returnsEmptyList() {
        server.expect(requestTo("http://localhost/location?session_key=9161"))
                .andRespond(withServerError());

        assertTrue(client.fetchLocations("9161", Optional.empty()).isEmpty());
    }

    @Test
    void fetchRaceControl_returnsParsedResponses() {
        String body =
                """
                [
                  {
                    "date": "2024-03-02T15:10:00+00:00",
                    "category": "SafetyCar",
                    "message": "SAFETY CAR DEPLOYED",
                    "session_key": 9161,
                    "meeting_key": 1219,
                    "lap_number": 12
                  }
                ]
                """;

        server.expect(requestTo("http://localhost/race_control?session_key=9161"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<OpenF1RaceControlResponse> messages = client.fetchRaceControl("9161", Optional.empty());

        assertEquals(1, messages.size());
        assertEquals("SAFETY CAR DEPLOYED", messages.getFirst().message());
        assertEquals(9161L, messages.getFirst().sessionKey());
    }

    @Test
    void fetchRaceControl_appendsDateFilterWhenSincePresent() {
        Instant since = Instant.parse("2024-03-02T14:00:00Z");

        server.expect(requestTo("http://localhost/race_control?session_key=9161&date%3E=2024-03-02T14:00:00Z"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(client.fetchRaceControl("9161", Optional.of(since)).isEmpty());
    }

    @Test
    void fetchRaceControl_onServerError_returnsEmptyList() {
        server.expect(requestTo("http://localhost/race_control?session_key=9161"))
                .andRespond(withServerError());

        assertTrue(client.fetchRaceControl("9161", Optional.empty()).isEmpty());
    }

    @Test
    void fetchSession_returnsFirstResult() {
        String body =
                """
                [
                  {
                    "session_key": 9161,
                    "meeting_key": 1219,
                    "session_name": "Race",
                    "circuit_short_name": "Bahrain",
                    "date_start": "2024-03-02T15:00:00+00:00"
                  }
                ]
                """;

        server.expect(requestTo("http://localhost/sessions?session_key=9161"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Optional<OpenF1SessionResponse> session = client.fetchSession("9161");

        assertTrue(session.isPresent());
        assertEquals(9161L, session.get().sessionKey());
        assertEquals("Race", session.get().sessionName());
    }
}
