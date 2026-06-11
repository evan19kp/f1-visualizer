package com.evanp.f1.ai.openai;

import com.evanp.f1.ai.config.OpenAiProperties;
import com.evanp.f1.core.event.RaceEvent;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String BASE_URL = "https://api.openai.com/v1";
    private static final String SYSTEM_PROMPT =
            "You are a concise Formula 1 race engineer. Respond in 2-3 sentences with "
                    + "actionable, factual commentary for the driver.";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public OpenAiClient(RestClient.Builder builder, OpenAiProperties properties) {
        this(builder, properties, true);
    }

    OpenAiClient(RestClient.Builder builder, OpenAiProperties properties, boolean applyTimeouts) {
        RestClient.Builder configured = builder.baseUrl(BASE_URL);
        if (applyTimeouts) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
            requestFactory.setReadTimeout(READ_TIMEOUT);
            configured = configured.requestFactory(requestFactory);
        }
        this.restClient = configured.build();
        this.properties = properties;
    }

    public Optional<String> generateCommentary(RaceEvent event) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.debug("OpenAI API key not configured; skipping chat completion");
            return Optional.empty();
        }

        try {
            ChatCompletionResponse response = restClient
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(new ChatCompletionRequest(
                            properties.model(),
                            List.of(
                                    new ChatMessage("system", SYSTEM_PROMPT),
                                    new ChatMessage("user", formatUserPrompt(event)))))
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null
                    || response.choices() == null
                    || response.choices().isEmpty()
                    || response.choices().getFirst().message() == null) {
                return Optional.empty();
            }
            String content = response.choices().getFirst().message().content();
            return content == null || content.isBlank() ? Optional.empty() : Optional.of(content.trim());
        } catch (RestClientException e) {
            log.warn("OpenAI chat completion failed for session {}: {}", event.sessionKey(), e.getMessage());
            return Optional.empty();
        }
    }

    private static String formatUserPrompt(RaceEvent event) {
        return event.type() + ": " + event.summary();
    }

    private record ChatCompletionRequest(String model, List<ChatMessage> messages) {}

    private record ChatMessage(String role, String content) {}

    private record ChatCompletionResponse(List<Choice> choices) {}

    private record Choice(ChatMessage message) {}
}
