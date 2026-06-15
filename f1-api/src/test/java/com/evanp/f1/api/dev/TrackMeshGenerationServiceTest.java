package com.evanp.f1.api.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.api.dto.TrackAssetResponse;
import com.evanp.f1.ingestion.SessionMetadataSync;
import com.evanp.f1.persistence.s3.TrackAssetService;
import com.evanp.f1.persistence.session.RaceSessionEntity;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TrackMeshGenerationServiceTest {

    private static final long SESSION_KEY = 9161L;

    @Mock
    private RaceSessionRepository raceSessionRepository;

    @Mock
    private SessionMetadataSync sessionMetadataSync;

    @Mock
    private TrackAssetService trackAssetService;

    @Mock
    private TrackMeshProcessRunner processRunner;

    @Test
    void generate_uploadsGlbAndReturnsPresignedUrl(@TempDir Path tempDir) throws Exception {
        Path trackMeshRoot = Files.createDirectory(tempDir.resolve("track-mesh"));
        RaceSessionEntity session = mock(RaceSessionEntity.class);
        when(session.getCircuitName()).thenReturn("Singapore");
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.of(session));
        when(processRunner.run(eq(trackMeshRoot), eq(SESSION_KEY), eq("singapore"), any()))
                .thenReturn(new TrackMeshProcessRunner.ProcessResult(0, ""));
        when(trackAssetService.getPresignedTrackUrl("singapore"))
                .thenReturn(Optional.of(new URL("https://s3.example.com/tracks/singapore.glb")));

        TrackMeshGenerationService service = new TrackMeshGenerationService(
                raceSessionRepository,
                sessionMetadataSync,
                trackAssetService,
                new DevProperties(true, trackMeshRoot.toString()),
                processRunner);

        TrackAssetResponse response = service.generate(SESSION_KEY);

        assertThat(response.circuitSlug()).isEqualTo("singapore");
        assertThat(response.url()).contains("singapore.glb");
        verify(trackAssetService).uploadTrackMesh(eq("singapore"), any());
    }

    @Test
    void generate_returnsNotFoundWhenCircuitNameMissing() {
        RaceSessionEntity session = mock(RaceSessionEntity.class);
        when(session.getCircuitName()).thenReturn("");
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.of(session));

        TrackMeshGenerationService service = new TrackMeshGenerationService(
                raceSessionRepository,
                sessionMetadataSync,
                trackAssetService,
                new DevProperties(true, "tools/track-mesh"),
                processRunner);

        assertThatThrownBy(() -> service.generate(SESSION_KEY)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void generate_returnsBadGatewayWhenProcessFails(@TempDir Path tempDir) throws Exception {
        Path trackMeshRoot = Files.createDirectory(tempDir.resolve("track-mesh"));
        RaceSessionEntity session = mock(RaceSessionEntity.class);
        when(session.getCircuitName()).thenReturn("Singapore");
        when(raceSessionRepository.findById(SESSION_KEY)).thenReturn(Optional.of(session));
        when(processRunner.run(eq(trackMeshRoot), eq(SESSION_KEY), eq("singapore"), any()))
                .thenReturn(new TrackMeshProcessRunner.ProcessResult(1, "generator failed"));

        TrackMeshGenerationService service = new TrackMeshGenerationService(
                raceSessionRepository,
                sessionMetadataSync,
                trackAssetService,
                new DevProperties(true, trackMeshRoot.toString()),
                processRunner);

        assertThatThrownBy(() -> service.generate(SESSION_KEY)).isInstanceOf(ResponseStatusException.class);
    }
}
