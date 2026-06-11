package com.evanp.f1.ingestion.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IngestionProperties.class)
public class IngestionConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
