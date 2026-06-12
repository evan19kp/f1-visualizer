package com.evanp.f1.api.rest;

import com.evanp.f1.api.dto.TrackAssetResponse;
import com.evanp.f1.persistence.s3.CircuitSlug;
import com.evanp.f1.persistence.s3.TrackAssetService;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class TrackAssetController {

    private final RaceSessionRepository raceSessionRepository;
    private final TrackAssetService trackAssetService;

    public TrackAssetController(RaceSessionRepository raceSessionRepository, TrackAssetService trackAssetService) {
        this.raceSessionRepository = raceSessionRepository;
        this.trackAssetService = trackAssetService;
    }

    @GetMapping("/{sessionKey}/track-asset")
    public ResponseEntity<TrackAssetResponse> getTrackAsset(@PathVariable long sessionKey) {
        return raceSessionRepository
                .findById(sessionKey)
                .flatMap(session -> {
                    String circuitSlug = CircuitSlug.fromCircuitName(session.getCircuitName());
                    return trackAssetService
                            .getPresignedTrackUrl(circuitSlug)
                            .map(url -> new TrackAssetResponse(url.toString(), circuitSlug));
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
