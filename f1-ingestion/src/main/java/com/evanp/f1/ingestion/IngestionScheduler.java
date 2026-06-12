package com.evanp.f1.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true")
public class IngestionScheduler {

    private final IngestionService ingestionService;
    private final StintIngestionService stintIngestionService;

    public IngestionScheduler(IngestionService ingestionService, StintIngestionService stintIngestionService) {
        this.ingestionService = ingestionService;
        this.stintIngestionService = stintIngestionService;
    }

    @Scheduled(fixedDelayString = "${app.openf1.poll-interval-ms}")
    public void tick() {
        ingestionService.pollOnce();
        stintIngestionService.pollOnce();
    }
}
