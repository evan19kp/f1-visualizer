package com.evanp.f1.api.playback;

import java.time.Instant;

public record PlaybackState(
        Instant start,
        Instant end,
        Instant current,
        State state,
        double speed,
        boolean historyLoaded) {

    public enum State {
        STOPPED,
        PLAYING,
        PAUSED
    }
}
