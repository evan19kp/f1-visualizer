package com.evanp.f1.api.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evanp.f1.ai.config.OpenAiProperties;
import org.junit.jupiter.api.Test;

class OpenAiKeyValidatorTest {

    private static final OpenAiProperties WITH_KEY = new OpenAiProperties("sk-prod-key", "gpt-4o-mini", 30);
    private static final OpenAiProperties WITHOUT_KEY = new OpenAiProperties("", "gpt-4o-mini", 30);

    @Test
    void skipsValidationWhenAiDisabled() {
        assertThatCode(() -> new OpenAiKeyValidator(false, WITHOUT_KEY)).doesNotThrowAnyException();
    }

    @Test
    void acceptsKeyWhenAiEnabled() {
        assertThatCode(() -> new OpenAiKeyValidator(true, WITH_KEY)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingKeyWhenAiEnabled() {
        assertThatThrownBy(() -> new OpenAiKeyValidator(true, WITHOUT_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY must be set");
    }
}
