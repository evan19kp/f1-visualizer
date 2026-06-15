package com.evanp.f1.ingestion;

import com.evanp.f1.ingestion.config.IngestionProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class IngestionStatusService {

    private final IngestionProperties properties;
    private final SessionKeyResolver sessionKeyResolver;
    private final Clock clock;
    private final AtomicReference<Instant> lastPollAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>("none");
    private final AtomicReference<Long> lastResolvedSessionKey = new AtomicReference<>();

    public IngestionStatusService(
            IngestionProperties properties, SessionKeyResolver sessionKeyResolver, Clock clock) {
        this.properties = properties;
        this.sessionKeyResolver = sessionKeyResolver;
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

    public Snapshot snapshot() {
        if (!properties.enabled()) {
            return new Snapshot(
                    false,
                    properties.sessionKey(),
                    sessionKeyResolver.resolveNumericKey(properties.sessionKey()),
                    lastPollAt.get(),
                    "ingestion_disabled");
        }
        Long cached = lastResolvedSessionKey.get();
        long resolved = cached != null
                ? cached
                : sessionKeyResolver.resolveNumericKey(properties.sessionKey());
        return new Snapshot(
                true,
                properties.sessionKey(),
                resolved,
                lastPollAt.get(),
                lastError.get() != null ? lastError.get() : "none");
    }

    public record Snapshot(
            boolean enabled,
            String configuredSessionKey,
            long resolvedSessionKey,
            Instant lastPollAt,
            String lastError) {}
}
