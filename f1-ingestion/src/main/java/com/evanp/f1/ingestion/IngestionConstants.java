package com.evanp.f1.ingestion;

import java.time.Duration;

public final class IngestionConstants {

    public static final Duration POLL_WINDOW = Duration.ofMinutes(5);

    private IngestionConstants() {}
}
