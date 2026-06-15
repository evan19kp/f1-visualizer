package com.evanp.f1.api.rest;

import com.evanp.f1.api.playback.PlaybackService;
import com.evanp.f1.api.playback.PlaybackState;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionKey}/playback")
public class PlaybackController {

    private final PlaybackService playbackService;

    public PlaybackController(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    @GetMapping
    public ResponseEntity<PlaybackState> getPlayback(@PathVariable long sessionKey) {
        return ResponseEntity.ok(playbackService.getState(sessionKey));
    }

    @PostMapping("/seek")
    public ResponseEntity<PlaybackState> seek(
            @PathVariable long sessionKey, @RequestBody SeekRequest request) {
        if (request == null || request.instant() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(playbackService.seek(sessionKey, request.instant()));
    }

    @PostMapping("/play")
    public ResponseEntity<PlaybackState> play(
            @PathVariable long sessionKey, @RequestBody(required = false) PlayRequest request) {
        double speed = request != null && request.speed() != null ? request.speed() : 1.0;
        return ResponseEntity.ok(playbackService.play(sessionKey, speed));
    }

    @PostMapping("/pause")
    public ResponseEntity<PlaybackState> pause(@PathVariable long sessionKey) {
        return ResponseEntity.ok(playbackService.pause(sessionKey));
    }

    public record SeekRequest(Instant instant) {}

    public record PlayRequest(Double speed) {}
}
