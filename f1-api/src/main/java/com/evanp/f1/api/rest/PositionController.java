package com.evanp.f1.api.rest;

import com.evanp.f1.api.dto.PositionDto;
import com.evanp.f1.api.dto.SessionBoundsDto;
import com.evanp.f1.core.position.PositionStore;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sessions/{sessionKey}")
public class PositionController {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 500;

    private final PositionStore positionStore;

    public PositionController(PositionStore positionStore) {
        this.positionStore = positionStore;
    }

    @GetMapping("/positions")
    public List<PositionDto> getPositions(
            @PathVariable long sessionKey,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        if (offset < 0 || limit < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "offset must be >= 0 and limit must be >= 1");
        }
        int effectiveLimit = Math.min(limit, MAX_LIMIT);
        return positionStore.getAllPositions(sessionKey).stream()
                .skip(offset)
                .limit(effectiveLimit)
                .map(PositionDto::from)
                .toList();
    }

    @GetMapping("/positions/{driverNumber}")
    public ResponseEntity<PositionDto> getDriverPosition(
            @PathVariable long sessionKey, @PathVariable int driverNumber) {
        return positionStore.getLatest(sessionKey, driverNumber)
                .map(PositionDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/bounds")
    public ResponseEntity<SessionBoundsDto> getBounds(@PathVariable long sessionKey) {
        return positionStore.getBounds(sessionKey)
                .map(SessionBoundsDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
