package com.evanp.f1.api.rest;

import com.evanp.f1.api.dto.SessionDto;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final RaceSessionRepository raceSessionRepository;

    public SessionController(RaceSessionRepository raceSessionRepository) {
        this.raceSessionRepository = raceSessionRepository;
    }

    @GetMapping
    public List<SessionDto> listSessions() {
        return raceSessionRepository.findAll().stream()
                .map(SessionDto::from)
                .toList();
    }

    @GetMapping("/{sessionKey}")
    public ResponseEntity<SessionDto> getSession(@PathVariable long sessionKey) {
        return raceSessionRepository.findById(sessionKey)
                .map(SessionDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
