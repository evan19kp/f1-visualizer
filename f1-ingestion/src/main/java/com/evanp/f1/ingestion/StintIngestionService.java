package com.evanp.f1.ingestion;

import com.evanp.f1.core.stint.StintSnapshot;
import com.evanp.f1.core.stint.StintStore;
import com.evanp.f1.ingestion.config.IngestionProperties;
import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1SessionResponse;
import com.evanp.f1.ingestion.openf1.OpenF1StintResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StintIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StintIngestionService.class);

    private final OpenF1Client openF1Client;
    private final StintStore stintStore;
    private final IngestionProperties properties;

    public StintIngestionService(
            OpenF1Client openF1Client, StintStore stintStore, IngestionProperties properties) {
        this.openF1Client = openF1Client;
        this.stintStore = stintStore;
        this.properties = properties;
    }

    public void pollOnce() {
        try {
            String configKey = properties.sessionKey();
            long sessionKey = resolveSessionKey(configKey);
            if (sessionKey < 0) {
                return;
            }

            List<OpenF1StintResponse> responses = openF1Client.fetchStints(configKey, Optional.empty());
            if (responses.isEmpty()) {
                return;
            }

            List<StintSnapshot> latestPerDriver = pickLatestPerDriver(responses, sessionKey);
            stintStore.save(sessionKey, latestPerDriver);
        } catch (Exception e) {
            log.error("Stint ingestion poll failed: {}", e.getMessage(), e);
        }
    }

    static List<StintSnapshot> pickLatestPerDriver(List<OpenF1StintResponse> responses, long sessionKey) {
        Map<Integer, OpenF1StintResponse> latestByDriver = new LinkedHashMap<>();
        for (OpenF1StintResponse response : responses) {
            latestByDriver.merge(
                    response.driverNumber(),
                    response,
                    (existing, incoming) -> incoming.stintNumber() >= existing.stintNumber() ? incoming : existing);
        }
        List<StintSnapshot> snapshots = new ArrayList<>(latestByDriver.size());
        for (OpenF1StintResponse response : latestByDriver.values()) {
            snapshots.add(toSnapshot(response, sessionKey));
        }
        return snapshots;
    }

    private static StintSnapshot toSnapshot(OpenF1StintResponse response, long sessionKey) {
        return new StintSnapshot(
                response.driverNumber(),
                sessionKey,
                response.compound(),
                response.stintNumber(),
                response.lapStart(),
                response.lapEnd(),
                response.tyreAgeAtStart());
    }

    private long resolveSessionKey(String configKey) {
        if (isNumericSessionKey(configKey)) {
            return Long.parseLong(configKey);
        }
        return openF1Client.fetchSession(configKey).map(OpenF1SessionResponse::sessionKey).orElse(-1L);
    }

    private static boolean isNumericSessionKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return false;
        }
        for (int i = 0; i < sessionKey.length(); i++) {
            if (!Character.isDigit(sessionKey.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
