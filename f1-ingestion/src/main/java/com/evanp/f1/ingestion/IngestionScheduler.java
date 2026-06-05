package com.evanp.f1.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true")
public class IngestionScheduler {

    private final IngestionService ingestionService;

    public IngestionScheduler(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Scheduled(fixedDelayString = "${app.openf1.poll-interval-ms}")
    public void tick() {
        ingestionService.pollOnce();
    }
}
