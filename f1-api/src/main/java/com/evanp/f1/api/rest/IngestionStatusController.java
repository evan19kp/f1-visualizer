package com.evanp.f1.api.rest;

import com.evanp.f1.ingestion.IngestionStatusService;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionStatusController {

    private final IngestionStatusService ingestionStatusService;

    public IngestionStatusController(IngestionStatusService ingestionStatusService) {
        this.ingestionStatusService = ingestionStatusService;
    }

    @GetMapping("/status")
    public ResponseEntity<IngestionStatusResponse> status() {
        IngestionStatusService.Snapshot snapshot = ingestionStatusService.snapshot();
        return ResponseEntity.ok(new IngestionStatusResponse(
                snapshot.enabled(),
                snapshot.configuredSessionKey(),
                snapshot.resolvedSessionKey(),
                snapshot.lastPollAt(),
                snapshot.lastError(),
                snapshot.autoBootstrap()));
    }

    public record IngestionStatusResponse(
            boolean enabled,
            String configuredSessionKey,
            long resolvedSessionKey,
            Instant lastPollAt,
            String lastError,
            boolean autoBootstrap) {}
}
