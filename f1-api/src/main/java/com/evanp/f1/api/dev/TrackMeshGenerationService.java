package com.evanp.f1.api.dev;

import com.evanp.f1.api.dto.TrackAssetResponse;
import com.evanp.f1.ingestion.SessionMetadataSync;
import com.evanp.f1.persistence.s3.CircuitSlug;
import com.evanp.f1.persistence.s3.TrackAssetService;
import com.evanp.f1.persistence.session.RaceSessionEntity;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrackMeshGenerationService {

    private final RaceSessionRepository raceSessionRepository;
    private final SessionMetadataSync sessionMetadataSync;
    private final TrackAssetService trackAssetService;
    private final DevProperties devProperties;
    private final TrackMeshProcessRunner processRunner;

    public TrackMeshGenerationService(
            RaceSessionRepository raceSessionRepository,
            SessionMetadataSync sessionMetadataSync,
            TrackAssetService trackAssetService,
            DevProperties devProperties,
            TrackMeshProcessRunner processRunner) {
        this.raceSessionRepository = raceSessionRepository;
        this.sessionMetadataSync = sessionMetadataSync;
        this.trackAssetService = trackAssetService;
        this.devProperties = devProperties;
        this.processRunner = processRunner;
    }

    public TrackAssetResponse generate(long sessionKey) {
        RaceSessionEntity session = resolveSession(sessionKey);
        String circuitSlug = CircuitSlug.fromCircuitName(session.getCircuitName());
        if (circuitSlug.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Session " + sessionKey + " has no circuit name");
        }

        Path trackMeshRoot = resolveTrackMeshRoot();
        Path outputPath = null;
        try {
            outputPath = Files.createTempFile("track-mesh-" + sessionKey + "-", ".glb");
            TrackMeshProcessRunner.ProcessResult result =
                    processRunner.run(trackMeshRoot, sessionKey, circuitSlug, outputPath);
            if (result.exitCode() != 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Track mesh generation failed: " + result.stderr());
            }
            if (!Files.isRegularFile(outputPath) || Files.size(outputPath) == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Track mesh generation produced an empty or missing GLB");
            }

            trackAssetService.uploadTrackMesh(circuitSlug, outputPath);
            URL url = trackAssetService
                    .getPresignedTrackUrl(circuitSlug)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "Uploaded track mesh is not readable from S3"));
            return new TrackAssetResponse(url.toString(), circuitSlug);
        } catch (IOException ioException) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to run track mesh generator: " + ioException.getMessage(),
                    ioException);
        } finally {
            if (outputPath != null) {
                try {
                    Files.deleteIfExists(outputPath);
                } catch (IOException ignored) {
                    // best-effort temp cleanup
                }
            }
        }
    }

    private RaceSessionEntity resolveSession(long sessionKey) {
        Optional<RaceSessionEntity> session = raceSessionRepository.findById(sessionKey);
        if (session.isPresent()) {
            return session.get();
        }
        sessionMetadataSync.syncIfNeeded(String.valueOf(sessionKey));
        return raceSessionRepository
                .findById(sessionKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Session " + sessionKey + " not found"));
    }

    private Path resolveTrackMeshRoot() {
        Path configured = Path.of(devProperties.trackMeshRoot());
        Path root = configured.isAbsolute() ? configured : Path.of(System.getProperty("user.dir")).resolve(configured);
        if (!Files.isDirectory(root)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Track mesh root not found: " + root);
        }
        return root;
    }
}
