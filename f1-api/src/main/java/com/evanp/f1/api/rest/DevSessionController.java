package com.evanp.f1.api.rest;

import com.evanp.f1.api.dev.SessionResetService;
import com.evanp.f1.api.dto.SessionResetResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/sessions")
public class DevSessionController {

    private final SessionResetService sessionResetService;

    public DevSessionController(SessionResetService sessionResetService) {
        this.sessionResetService = sessionResetService;
    }

    @PostMapping("/{sessionKey}/reset")
    public ResponseEntity<SessionResetResponse> resetSession(@PathVariable long sessionKey) {
        return ResponseEntity.ok(sessionResetService.reset(sessionKey));
    }
}
