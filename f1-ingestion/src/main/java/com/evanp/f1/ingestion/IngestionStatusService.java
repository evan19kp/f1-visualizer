package com.evanp.f1.ingestion;

import com.evanp.f1.core.position.PositionStore;
import com.evanp.f1.core.position.SessionTimeRange;
import com.evanp.f1.ingestion.config.IngestionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class IngestionStatusService {

    private final IngestionProperties properties;
    private final SessionKeyResolver sessionKeyResolver;
    private final PositionStore positionStore;
    private final Clock clock;
    private final AtomicReference<Instant> lastPollAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>("none");
    private final AtomicReference<Long> lastResolvedSessionKey = new AtomicReference<>();
    private final AtomicReference<BootstrapStatus> bootstrapStatus =
            new AtomicReference<>(BootstrapStatus.IDLE);

    public IngestionStatusService(
            IngestionProperties properties,
            SessionKeyResolver sessionKeyResolver,
            PositionStore positionStore,
            Clock clock) {
        this.properties = properties;
        this.sessionKeyResolver = sessionKeyResolver;
        this.positionStore = positionStore;
        this.clock = clock;
    }

    public void recordPollSuccess(long resolvedSessionKey) {
        lastPollAt.set(clock.instant());
        lastError.set("none");
        lastResolvedSessionKey.set(resolvedSessionKey);
    }

    public void recordOpenF1Error(String errorCode) {
        lastPollAt.set(clock.instant());
        lastError.set(errorCode);
    }

    public void markBootstrapRunning() {
        bootstrapStatus.set(BootstrapStatus.RUNNING);
    }

    public void markBootstrapComplete() {
        bootstrapStatus.set(BootstrapStatus.COMPLETE);
    }

    public void markBootstrapFailed() {
        bootstrapStatus.set(BootstrapStatus.FAILED);
    }

    public boolean isBootstrapRunning() {
        return bootstrapStatus.get() == BootstrapStatus.RUNNING;
    }

    public boolean isBootstrapComplete() {
        return bootstrapStatus.get() == BootstrapStatus.COMPLETE;
    }

    public Snapshot snapshot() {
        long resolved = resolveSessionKey();
        if (!properties.enabled()) {
            return new Snapshot(
                    false,
                    properties.sessionKey(),
                    resolved,
                    lastPollAt.get(),
                    "ingestion_disabled",
                    properties.autoBootstrap(),
                    bootstrapStatus.get(),
                    false);
        }
        return new Snapshot(
                true,
                properties.sessionKey(),
                resolved,
                lastPollAt.get(),
                lastError.get() != null ? lastError.get() : "none",
                properties.autoBootstrap(),
                bootstrapStatus.get(),
                isHistoryReady(resolved));
    }

    private long resolveSessionKey() {
        Long cached = lastResolvedSessionKey.get();
        if (cached != null) {
            return cached;
        }
        return sessionKeyResolver.resolveNumericKey(properties.sessionKey());
    }

    boolean isHistoryReady(long sessionKey) {
        if (sessionKey < 0 || !positionStore.hasHistory(sessionKey)) {
            return false;
        }
        if (bootstrapStatus.get() == BootstrapStatus.COMPLETE) {
            return true;
        }
        Optional<SessionTimeRange> range = positionStore.getHistoryTimeRange(sessionKey);
        if (range.isEmpty()) {
            return false;
        }
        long spanMinutes = Duration.between(range.get().start(), range.get().end()).toMinutes();
        return spanMinutes >= 45;
    }

    public record Snapshot(
            boolean enabled,
            String configuredSessionKey,
            long resolvedSessionKey,
            Instant lastPollAt,
            String lastError,
            boolean autoBootstrap,
            BootstrapStatus bootstrapStatus,
            boolean historyReady) {}
}
