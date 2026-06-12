package com.evanp.f1.ai.config;

import com.evanp.f1.ai.openai.OpenAiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(OpenAiProperties.class)
public class AiConfiguration {

    @Bean
    OpenAiClient openAiClient(RestClient.Builder restClientBuilder, OpenAiProperties properties) {
        return new OpenAiClient(restClientBuilder, properties);
    }
}
