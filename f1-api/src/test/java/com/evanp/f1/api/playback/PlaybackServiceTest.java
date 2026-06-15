package com.evanp.f1.api.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionTimeRange;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaybackServiceTest {

    private static final long SESSION_KEY = 9161L;
    private static final Instant START = Instant.parse("2024-03-02T15:00:00Z");
    private static final Instant END = Instant.parse("2024-03-02T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);

    @Mock
    private PositionStore positionStore;

    private PlaybackService playbackService;

    @BeforeEach
    void setUp() {
        playbackService = new PlaybackService(positionStore, CLOCK);
        when(positionStore.hasHistory(SESSION_KEY)).thenReturn(true);
        when(positionStore.getHistoryTimeRange(SESSION_KEY))
                .thenReturn(Optional.of(new SessionTimeRange(START, END)));
    }

    @Test
    void play_publishesFrameAtCurrentTime() {
        NormalizedPosition position = new NormalizedPosition(44, SESSION_KEY, START, 0.1, 0.2, 0.3);
        when(positionStore.getFrameAt(SESSION_KEY, START)).thenReturn(List.of(position));

        PlaybackState state = playbackService.play(SESSION_KEY, 1.0);

        assertThat(state.state()).isEqualTo(PlaybackState.State.PLAYING);
        verify(positionStore).savePositions(SESSION_KEY, List.of(position));
    }

    @Test
    void seek_jumpsToInstantAndPublishes() {
        Instant seekTo = START.plusSeconds(30);
        NormalizedPosition position = new NormalizedPosition(1, SESSION_KEY, seekTo, 0.4, 0.5, 0.6);
        when(positionStore.getFrameAt(eq(SESSION_KEY), eq(seekTo))).thenReturn(List.of(position));

        PlaybackState state = playbackService.seek(SESSION_KEY, seekTo);

        assertThat(state.current()).isEqualTo(seekTo);
        verify(positionStore).savePositions(SESSION_KEY, List.of(position));
    }
}
