package com.evanp.f1.ingestion.openf1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@ExtendWith(OutputCaptureExtension.class)
class OpenF1ClientTest {

    private MockRestServiceServer server;
    private OpenF1Client client;
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenF1Client(builder, "http://localhost", "", "", "", "http://localhost/token", 30_000, clock);
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

        List<OpenF1LocationResponse> locations = client.fetchLocations("9161", Optional.empty(), Optional.empty());

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

        assertTrue(client.fetchLocations("9161", Optional.of(since), Optional.empty()).isEmpty());
    }

    @Test
    void fetchLocations_appendsUntilFilterWhenPresent() {
        Instant since = Instant.parse("2024-03-02T14:00:00Z");
        Instant until = Instant.parse("2024-03-02T14:05:00Z");

        server.expect(requestTo(
                        "http://localhost/location?session_key=9161&date%3E=2024-03-02T14:00:00Z&date%3C=2024-03-02T14:05:00Z"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(client.fetchLocations("9161", Optional.of(since), Optional.of(until)).isEmpty());
    }

    @Test
    void fetchLocations_onServerError_returnsEmptyList() {
        server.expect(requestTo("http://localhost/location?session_key=9161"))
                .andRespond(withServerError());

        assertTrue(client.fetchLocations("9161", Optional.empty(), Optional.empty()).isEmpty());
    }

    @Test
    void fetchLocations_onNotFound_returnsEmptyListWithoutErrorLog(CapturedOutput output) {
        server.expect(requestTo("http://localhost/location?session_key=9161"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"No results found.\"}"));

        assertTrue(client.fetchLocations("9161", Optional.empty(), Optional.empty()).isEmpty());
        assertFalse(output.getAll().contains("OpenF1 /location request failed"));
    }

    @Test
    void fetchLocations_sendsConfiguredAccessToken() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenF1Client(
                builder, "http://localhost", "static-token", "", "", "http://localhost/token", 30_000, clock);

        server.expect(requestTo("http://localhost/location?session_key=9161"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer static-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(client.fetchLocations("9161", Optional.empty(), Optional.empty()).isEmpty());
    }

    @Test
    void fetchLocations_fetchesAndCachesOAuthToken() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenF1Client(
                builder,
                "http://localhost",
                "",
                "driver@example.com",
                "secret",
                "http://localhost/token",
                30_000,
                clock);

        server.expect(requestTo("http://localhost/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"oauth-token\",\"expires_in\":\"3600\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/location?session_key=9161"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer oauth-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/race_control?session_key=9161"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer oauth-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(client.fetchLocations("9161", Optional.empty(), Optional.empty()).isEmpty());
        assertTrue(client.fetchRaceControl("9161", Optional.empty()).isEmpty());
    }

    @Test
    void fetchLocations_skipsTokenRequestDuringFailureCooldown() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenF1Client(
                builder,
                "http://localhost",
                "",
                "driver@example.com",
                "secret",
                "http://localhost/token",
                30_000,
                clock);

        server.expect(requestTo("http://localhost/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        server.expect(requestTo("http://localhost/location?session_key=9161"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/race_control?session_key=9161"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(client.fetchLocations("9161", Optional.empty(), Optional.empty()).isEmpty());
        assertTrue(client.fetchRaceControl("9161", Optional.empty()).isEmpty());
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
    void fetchRaceControl_onNotFound_returnsEmptyListWithoutErrorLog(CapturedOutput output) {
        server.expect(requestTo("http://localhost/race_control?session_key=9161"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"No results found.\"}"));

        assertTrue(client.fetchRaceControl("9161", Optional.empty()).isEmpty());
        assertFalse(output.getAll().contains("OpenF1 /race_control request failed"));
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

    @Test
    void fetchSession_onNotFound_returnsEmptyWithoutErrorLog(CapturedOutput output) {
        server.expect(requestTo("http://localhost/sessions?session_key=9161"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"No results found.\"}"));

        assertTrue(client.fetchSession("9161").isEmpty());
        assertFalse(output.getAll().contains("OpenF1 /sessions request failed"));
    }
}
