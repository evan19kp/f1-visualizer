package com.evanp.f1.api;

import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestInfrastructureConfiguration.class)
@ActiveProfiles("test")
class F1VisualizerApplicationTests {

    @MockitoBean
    private PositionStore positionStore;

    @MockitoBean
    private RaceSessionRepository raceSessionRepository;

    @MockitoBean
    private OpenF1Client openF1Client;

    @Test
    void contextLoads() {
    }
}
