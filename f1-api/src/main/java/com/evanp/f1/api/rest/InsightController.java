package com.evanp.f1.api.rest;

import com.evanp.f1.ai.insight.InsightStore;
import com.evanp.f1.api.dto.RaceInsightDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sessions/{sessionKey}")
public class InsightController {

    static final int MAX_LIMIT = 50;

    private final InsightStore insightStore;

    public InsightController(InsightStore insightStore) {
        this.insightStore = insightStore;
    }

    @GetMapping("/insights")
    public List<RaceInsightDto> getInsights(
            @PathVariable long sessionKey, @RequestParam(defaultValue = "10") int limit) {
        if (limit < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be >= 1");
        }
        int effectiveLimit = Math.min(limit, MAX_LIMIT);
        return insightStore.getRecent(sessionKey, effectiveLimit).stream()
                .map(RaceInsightDto::from)
                .toList();
    }
}
