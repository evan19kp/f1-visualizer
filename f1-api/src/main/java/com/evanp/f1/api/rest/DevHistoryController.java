package com.evanp.f1.api.rest;

import com.evanp.f1.ingestion.SessionHistoryBackfillService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/sessions")
public class DevHistoryController {

    private final SessionHistoryBackfillService backfillService;

    public DevHistoryController(SessionHistoryBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @PostMapping("/{sessionKey}/history/backfill")
    public ResponseEntity<SessionHistoryBackfillService.BackfillResult> backfill(
            @PathVariable String sessionKey) {
        return ResponseEntity.ok(backfillService.backfill(sessionKey));
    }
}
