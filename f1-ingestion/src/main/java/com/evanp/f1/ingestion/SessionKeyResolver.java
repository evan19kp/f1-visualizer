package com.evanp.f1.ingestion;

import com.evanp.f1.ingestion.openf1.OpenF1Client;
import com.evanp.f1.ingestion.openf1.OpenF1SessionResponse;
import org.springframework.stereotype.Component;

@Component
public class SessionKeyResolver {

    private final OpenF1Client openF1Client;

    public SessionKeyResolver(OpenF1Client openF1Client) {
        this.openF1Client = openF1Client;
    }

    public long resolveNumericKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return -1L;
        }
        if (isNumericSessionKey(configKey)) {
            try {
                return Long.parseLong(configKey);
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        return openF1Client.fetchSession(configKey).map(OpenF1SessionResponse::sessionKey).orElse(-1L);
    }

    private static boolean isNumericSessionKey(String sessionKey) {
        for (int i = 0; i < sessionKey.length(); i++) {
            if (!Character.isDigit(sessionKey.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
