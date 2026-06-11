package com.evanp.f1.ingestion.openf1;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenF1Client {

    private static final Logger log = LoggerFactory.getLogger(OpenF1Client.class);

    private final RestClient restClient;

    public OpenF1Client(RestClient.Builder builder, @Value("${app.openf1.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public List<OpenF1LocationResponse> fetchLocations(String sessionKey, Optional<Instant> since) {
        try {
            List<OpenF1LocationResponse> locations =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder -> {
                                        uriBuilder.path("/location").queryParam("session_key", sessionKey);
                                        since.ifPresent(instant -> uriBuilder.queryParam("date>", instant));
                                        return uriBuilder.build();
                                    })
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});

            return locations != null ? locations : List.of();
        } catch (RestClientException e) {
            log.error("OpenF1 /location request failed for session {}: {}", sessionKey, e.getMessage());
            return List.of();
        }
    }

    public List<OpenF1RaceControlResponse> fetchRaceControl(String sessionKey, Optional<Instant> since) {
        try {
            List<OpenF1RaceControlResponse> messages =
                    restClient
                            .get()
                            .uri(uriBuilder -> {
                                uriBuilder.path("/race_control").queryParam("session_key", sessionKey);
                                since.ifPresent(instant -> uriBuilder.queryParam("date>", instant));
                                return uriBuilder.build();
                            })
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});

            return messages != null ? messages : List.of();
        } catch (RestClientException e) {
            log.error("OpenF1 /race_control request failed for session {}: {}", sessionKey, e.getMessage());
            return List.of();
        }
    }

    public Optional<OpenF1SessionResponse> fetchSession(String sessionKey) {
        try {
            OpenF1SessionResponse[] sessions =
                    restClient
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder.path("/sessions").queryParam("session_key", sessionKey).build())
                            .retrieve()
                            .body(OpenF1SessionResponse[].class);

            if (sessions == null || sessions.length == 0) {
                return Optional.empty();
            }
            return Optional.of(sessions[0]);
        } catch (RestClientException e) {
            log.error("OpenF1 /sessions request failed for session {}: {}", sessionKey, e.getMessage());
            return Optional.empty();
        }
    }
}
