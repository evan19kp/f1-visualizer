package com.evanp.f1.ingestion;

import com.evanp.f1.ingestion.openf1.OpenF1Client;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true")
public class IngestionScheduler {

    private final IngestionService ingestionService;
    private final StintIngestionService stintIngestionService;
    private final OpenF1Client openF1Client;
    private final IngestionStatusService ingestionStatusService;

    public IngestionScheduler(
            IngestionService ingestionService,
            StintIngestionService stintIngestionService,
            OpenF1Client openF1Client,
            IngestionStatusService ingestionStatusService) {
        this.ingestionService = ingestionService;
        this.stintIngestionService = stintIngestionService;
        this.openF1Client = openF1Client;
        this.ingestionStatusService = ingestionStatusService;
    }

    @Scheduled(fixedDelayString = "${app.openf1.poll-interval-ms}")
    public void tick() {
        if (openF1Client.isRateLimited() || ingestionStatusService.isBootstrapRunning()) {
            return;
        }
        ingestionService.pollOnce();
    }

    @Scheduled(fixedDelayString = "${app.openf1.stint-poll-interval-ms:30000}")
    public void pollStints() {
        if (openF1Client.isRateLimited() || ingestionStatusService.isBootstrapRunning()) {
            return;
        }
        stintIngestionService.pollOnce();
    }
}
