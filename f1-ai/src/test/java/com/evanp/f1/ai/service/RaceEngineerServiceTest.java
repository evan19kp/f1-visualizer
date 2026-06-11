package com.evanp.f1.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.ai.config.OpenAiProperties;
import com.evanp.f1.ai.insight.InsightStore;
import com.evanp.f1.ai.openai.OpenAiClient;
import com.evanp.f1.core.event.RaceEvent;
import com.evanp.f1.core.event.RaceEventType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RaceEngineerServiceTest {

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private InsightStore insightStore;

    private RaceEngineerService service;

    @BeforeEach
    void setUp() {
        service = new RaceEngineerService(openAiClient, insightStore, new OpenAiProperties("key", "gpt-4o-mini", 30));
    }

    @Test
    void rateLimit_skipsSecondCallWithinWindow() {
        RaceEvent event = new RaceEvent(9161L, Instant.now(), RaceEventType.UNDERCUT, "Leader pits");
        when(openAiClient.generateCommentary(event)).thenReturn(Optional.of("Stay out one more lap."));

        service.generateCommentary(event);
        service.generateCommentary(event);

        verify(openAiClient, times(1)).generateCommentary(event);
        verify(insightStore, times(1)).save(eq(9161L), any());
    }
}
