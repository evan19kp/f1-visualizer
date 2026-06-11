package com.evanp.f1.api.rest;

import com.evanp.f1.api.dto.PositionDto;
import com.evanp.f1.api.dto.SessionBoundsDto;
import com.evanp.f1.core.position.PositionStore;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionKey}")
public class PositionController {

    private final PositionStore positionStore;

    public PositionController(PositionStore positionStore) {
        this.positionStore = positionStore;
    }

    @GetMapping("/positions")
    public List<PositionDto> getPositions(@PathVariable long sessionKey) {
        return positionStore.getAllPositions(sessionKey).stream()
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
