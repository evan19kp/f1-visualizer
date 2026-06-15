package com.evanp.f1.api.rest;

import com.evanp.f1.api.dev.TrackMeshGenerationService;
import com.evanp.f1.api.dto.TrackAssetResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/sessions")
public class DevTrackController {

    private final TrackMeshGenerationService trackMeshGenerationService;

    public DevTrackController(TrackMeshGenerationService trackMeshGenerationService) {
        this.trackMeshGenerationService = trackMeshGenerationService;
    }

    @PostMapping("/{sessionKey}/track-mesh/generate")
    public ResponseEntity<TrackAssetResponse> generateTrackMesh(@PathVariable long sessionKey) {
        return ResponseEntity.ok(trackMeshGenerationService.generate(sessionKey));
    }
}
