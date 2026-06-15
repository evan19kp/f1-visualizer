package com.evanp.f1.api.playback;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionTimeRange;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PlaybackService {

    private static final Logger log = LoggerFactory.getLogger(PlaybackService.class);
    private static final Duration TICK_INTERVAL = Duration.ofMillis(200);

    private final PositionStore positionStore;
    private final Clock clock;
    private final Map<Long, SessionPlayback> sessions = new ConcurrentHashMap<>();

    public PlaybackService(PositionStore positionStore, Clock clock) {
        this.positionStore = positionStore;
        this.clock = clock;
    }

    public PlaybackState getState(long sessionKey) {
        SessionPlayback playback = sessions.computeIfAbsent(sessionKey, SessionPlayback::new);
        boolean historyLoaded = positionStore.hasHistory(sessionKey);
        Optional<SessionTimeRange> range = positionStore.getHistoryTimeRange(sessionKey);
        Instant start = range.map(SessionTimeRange::start).orElse(Instant.EPOCH);
        Instant end = range.map(SessionTimeRange::end).orElse(Instant.EPOCH);
        if (playback.currentTime == null && range.isPresent()) {
            playback.currentTime = start;
        }
        return new PlaybackState(
                start,
                end,
                playback.currentTime != null ? playback.currentTime : start,
                playback.state,
                playback.speed,
                historyLoaded);
    }

    public PlaybackState play(long sessionKey, double speed) {
        SessionPlayback playback = sessions.computeIfAbsent(sessionKey, SessionPlayback::new);
        playback.speed = clampSpeed(speed);
        playback.state = PlaybackState.State.PLAYING;
        playback.lastTickAt = clock.instant();
        positionStore.getHistoryTimeRange(sessionKey).ifPresent(range -> {
            if (playback.currentTime == null) {
                playback.currentTime = range.start();
            }
        });
        publishFrame(sessionKey, playback);
        return getState(sessionKey);
    }

    public PlaybackState pause(long sessionKey) {
        SessionPlayback playback = sessions.computeIfAbsent(sessionKey, SessionPlayback::new);
        playback.state = PlaybackState.State.PAUSED;
        return getState(sessionKey);
    }

    public PlaybackState seek(long sessionKey, Instant instant) {
        SessionPlayback playback = sessions.computeIfAbsent(sessionKey, SessionPlayback::new);
        playback.currentTime = instant;
        playback.lastTickAt = clock.instant();
        publishFrame(sessionKey, playback);
        return getState(sessionKey);
    }

    @Scheduled(fixedRate = 200)
    void tick() {
        Instant now = clock.instant();
        for (Map.Entry<Long, SessionPlayback> entry : sessions.entrySet()) {
            SessionPlayback playback = entry.getValue();
            if (playback.state != PlaybackState.State.PLAYING || playback.currentTime == null) {
                continue;
            }
            Duration elapsed = Duration.between(playback.lastTickAt, now);
            playback.lastTickAt = now;
            double advanceSeconds = elapsed.toMillis() / 1000.0 * playback.speed;
            playback.currentTime = playback.currentTime.plusMillis((long) (advanceSeconds * 1000));

            Optional<SessionTimeRange> range = positionStore.getHistoryTimeRange(entry.getKey());
            if (range.isPresent() && playback.currentTime.isAfter(range.get().end())) {
                playback.currentTime = range.get().end();
                playback.state = PlaybackState.State.PAUSED;
            }
            publishFrame(entry.getKey(), playback);
        }
    }

    private void publishFrame(long sessionKey, SessionPlayback playback) {
        if (playback.currentTime == null) {
            return;
        }
        List<NormalizedPosition> frame = positionStore.getFrameAt(sessionKey, playback.currentTime);
        if (frame.isEmpty()) {
            return;
        }
        positionStore.savePositions(sessionKey, frame);
    }

    private static double clampSpeed(double speed) {
        if (speed <= 0) {
            return 1.0;
        }
        if (speed > 8.0) {
            return 8.0;
        }
        return speed;
    }

    private static final class SessionPlayback {
        private PlaybackState.State state = PlaybackState.State.STOPPED;
        private Instant currentTime;
        private double speed = 1.0;
        private Instant lastTickAt = Instant.EPOCH;

        private SessionPlayback(long sessionKey) {}
    }
}
