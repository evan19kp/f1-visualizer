package com.evanp.f1.api.security;

import com.evanp.f1.ai.config.OpenAiProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class OpenAiKeyValidator {

    public OpenAiKeyValidator(
            @Value("${app.ai.enabled:false}") boolean aiEnabled, OpenAiProperties openAiProperties) {
        if (aiEnabled && !StringUtils.hasText(openAiProperties.apiKey())) {
            throw new IllegalStateException("OPENAI_API_KEY must be set when AI_ENABLED=true in production");
        }
    }
}
