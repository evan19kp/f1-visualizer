package com.evanp.f1.ingestion.ai;

import com.evanp.f1.core.event.RaceEvent;
import com.evanp.f1.core.event.RaceEventType;
import com.evanp.f1.ingestion.openf1.OpenF1RaceControlResponse;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RaceControlEventDetector {

    public Optional<RaceEvent> detect(OpenF1RaceControlResponse message) {
        String summary = message.message();
        if (summary == null || summary.isBlank()) {
            return Optional.empty();
        }

        String upper = summary.toUpperCase(Locale.ROOT);
        String flag = message.flag() != null ? message.flag().toUpperCase(Locale.ROOT) : "";
        String category = message.category() != null ? message.category().toUpperCase(Locale.ROOT) : "";

        if (isSafetyCarSignal(upper, flag, category)) {
            return Optional.of(new RaceEvent(
                    message.sessionKey(), message.date(), RaceEventType.SAFETY_CAR, summary));
        }
        if (isPitWindowSignal(upper, category)) {
            return Optional.of(new RaceEvent(
                    message.sessionKey(), message.date(), RaceEventType.PIT_WINDOW, summary));
        }
        return Optional.empty();
    }

    private static boolean isSafetyCarSignal(String message, String flag, String category) {
        return message.contains("SAFETY CAR")
                || message.contains("VIRTUAL SAFETY CAR")
                || message.contains(" VSC ")
                || message.startsWith("VSC ")
                || "YELLOW".equals(flag)
                || category.contains("SAFETY");
    }

    private static boolean isPitWindowSignal(String message, String category) {
        return category.contains("PIT")
                || message.contains("PIT LANE")
                || message.contains("PIT WINDOW")
                || message.contains("PIT STOP");
    }
}
