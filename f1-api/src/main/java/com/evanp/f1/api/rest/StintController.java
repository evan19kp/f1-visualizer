package com.evanp.f1.api.rest;

import com.evanp.f1.api.dto.StintDto;
import com.evanp.f1.core.stint.StintStore;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionKey}")
public class StintController {

    private final StintStore stintStore;

    public StintController(StintStore stintStore) {
        this.stintStore = stintStore;
    }

    @GetMapping("/stints")
    public List<StintDto> getStints(@PathVariable long sessionKey) {
        return stintStore.getAll(sessionKey).stream().map(StintDto::from).toList();
    }

    @GetMapping("/stints/{driverNumber}")
    public ResponseEntity<StintDto> getDriverStint(
            @PathVariable long sessionKey, @PathVariable int driverNumber) {
        return stintStore.getLatest(sessionKey, driverNumber)
                .map(StintDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
