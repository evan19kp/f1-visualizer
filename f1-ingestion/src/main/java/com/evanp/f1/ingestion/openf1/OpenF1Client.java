package com.evanp.f1.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenF1Client {

    private static final Logger log = LoggerFactory.getLogger(OpenF1Client.class);

    private final RestClient restClient;
    private final String configuredAccessToken;
    private final String username;
    private final String password;
    private final String tokenUrl;
    private final Clock clock;
    private volatile CachedToken cachedToken = CachedToken.empty();

    public OpenF1Client(
            RestClient.Builder builder,
            @Value("${app.openf1.base-url}") String baseUrl,
            @Value("${app.openf1.access-token:}") String configuredAccessToken,
            @Value("${app.openf1.username:}") String username,
            @Value("${app.openf1.password:}") String password,
            @Value("${app.openf1.token-url:https://api.openf1.org/token}") String tokenUrl,
            Clock clock) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.configuredAccessToken = configuredAccessToken;
        this.username = username;
        this.password = password;
        this.tokenUrl = tokenUrl;
        this.clock = clock;
    }

    public List<OpenF1LocationResponse> fetchLocations(
            String sessionKey, Optional<Instant> since, Optional<Instant> until) {
        try {
            RestClient.RequestHeadersSpec<?> request =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder -> {
                                        uriBuilder.path("/location").queryParam("session_key", sessionKey);
                                        since.ifPresent(instant -> uriBuilder.queryParam("date>", instant));
                                        until.ifPresent(instant -> uriBuilder.queryParam("date<", instant));
                                        return uriBuilder.build();
                                    });

            List<OpenF1LocationResponse> locations =
                    authorize(request)
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});

            return locations != null ? locations : List.of();
        } catch (RestClientException e) {
            if (isNotFound(e)) {
                return List.of();
            }
            log.error("OpenF1 /location request failed for session {}: {}", sessionKey, e.getMessage());
            return List.of();
        }
    }

    public List<OpenF1RaceControlResponse> fetchRaceControl(String sessionKey, Optional<Instant> since) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient
                    .get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/race_control").queryParam("session_key", sessionKey);
                        since.ifPresent(instant -> uriBuilder.queryParam("date>", instant));
                        return uriBuilder.build();
                    });

            List<OpenF1RaceControlResponse> messages =
                    authorize(request)
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});

            return messages != null ? messages : List.of();
        } catch (RestClientException e) {
            if (isNotFound(e)) {
                return List.of();
            }
            log.error("OpenF1 /race_control request failed for session {}: {}", sessionKey, e.getMessage());
            return List.of();
        }
    }

    public Optional<OpenF1SessionResponse> fetchSession(String sessionKey) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder.path("/sessions").queryParam("session_key", sessionKey).build());

            OpenF1SessionResponse[] sessions =
                    authorize(request)
                            .retrieve()
                            .body(OpenF1SessionResponse[].class);

            if (sessions == null || sessions.length == 0) {
                return Optional.empty();
            }
            return Optional.of(sessions[0]);
        } catch (RestClientException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            log.error("OpenF1 /sessions request failed for session {}: {}", sessionKey, e.getMessage());
            return Optional.empty();
        }
    }

    private RestClient.RequestHeadersSpec<?> authorize(RestClient.RequestHeadersSpec<?> request) {
        String accessToken = resolveAccessToken();
        if (!StringUtils.hasText(accessToken)) {
            return request;
        }
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private synchronized String resolveAccessToken() {
        if (StringUtils.hasText(configuredAccessToken)) {
            return configuredAccessToken.trim();
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return "";
        }
        Instant now = clock.instant();
        if (cachedToken.isValidAt(now)) {
            return cachedToken.value();
        }
        Optional<CachedToken> freshToken = requestAccessToken(now);
        freshToken.ifPresent(token -> cachedToken = token);
        return freshToken.map(CachedToken::value).orElse("");
    }

    private Optional<CachedToken> requestAccessToken(Instant now) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);

        try {
            TokenResponse response = restClient
                    .post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken())) {
                log.warn("OpenF1 token request succeeded without an access token");
                return Optional.empty();
            }
            return Optional.of(new CachedToken(
                    response.accessToken(), now.plusSeconds(Math.max(1L, response.expiresInSeconds()))));
        } catch (RestClientException e) {
            log.error("OpenF1 token request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isNotFound(RestClientException exception) {
        return exception instanceof RestClientResponseException responseException
                && responseException.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND);
    }

    private record CachedToken(String value, Instant expiresAt) {
        private static CachedToken empty() {
            return new CachedToken("", Instant.EPOCH);
        }

        private boolean isValidAt(Instant instant) {
            return StringUtils.hasText(value) && expiresAt.isAfter(instant.plusSeconds(60));
        }
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") String expiresIn) {
        private long expiresInSeconds() {
            if (!StringUtils.hasText(expiresIn)) {
                return 3600L;
            }
            try {
                return Long.parseLong(expiresIn);
            } catch (NumberFormatException e) {
                return 3600L;
            }
        }
    }
}
