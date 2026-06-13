package com.evanp.f1.ingestion.normalize;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.SessionBounds;
import com.evanp.f1.ingestion.openf1.OpenF1LocationResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CoordinateNormalizer {

    /**
     * OpenF1 /location uses x-y as the track plane; z is a narrow session axis (~200 m).
     * Scene space (and the frontend) uses x-z horizontal and y for elevation:
     *   scene x ← openF1 x, scene y ← openF1 z, scene z ← openF1 y
     */
    public NormalizationResult normalize(List<OpenF1LocationResponse> samples, SessionBounds bounds) {
        SessionBounds updatedBounds = bounds.isInitialized() ? bounds : SessionBounds.empty();
        for (OpenF1LocationResponse sample : samples) {
            updatedBounds = updatedBounds.expand(sample.x(), sample.y(), sample.z());
        }

        List<NormalizedPosition> positions = new ArrayList<>(samples.size());
        for (OpenF1LocationResponse sample : samples) {
            positions.add(toScenePosition(sample, updatedBounds));
        }

        return new NormalizationResult(positions, updatedBounds);
    }

    public NormalizedPosition renormalize(
            NormalizedPosition position, SessionBounds oldBounds, SessionBounds newBounds) {
        double openF1X = denormalizeAxis(position.x(), oldBounds.minX(), oldBounds.maxX());
        double openF1Y = denormalizeAxis(position.z(), oldBounds.minY(), oldBounds.maxY());
        double openF1Z = denormalizeAxis(position.y(), oldBounds.minZ(), oldBounds.maxZ());
        return new NormalizedPosition(
                position.driverNumber(),
                position.sessionKey(),
                position.timestamp(),
                normalizeAxis(openF1X, newBounds.minX(), newBounds.maxX()),
                normalizeAxis(openF1Z, newBounds.minZ(), newBounds.maxZ()),
                normalizeAxis(openF1Y, newBounds.minY(), newBounds.maxY()));
    }

    private static NormalizedPosition toScenePosition(
            OpenF1LocationResponse sample, SessionBounds bounds) {
        return new NormalizedPosition(
                sample.driverNumber(),
                sample.sessionKey(),
                sample.date(),
                normalizeAxis(sample.x(), bounds.minX(), bounds.maxX()),
                normalizeAxis(sample.z(), bounds.minZ(), bounds.maxZ()),
                normalizeAxis(sample.y(), bounds.minY(), bounds.maxY()));
    }

    private static double denormalizeAxis(double normalized, double min, double max) {
        if (max == min) {
            return min;
        }
        return min + (normalized + 1.0) / 2.0 * (max - min);
    }

    private static double normalizeAxis(double value, double min, double max) {
        if (max == min) {
            return 0.0;
        }
        double normalized = 2.0 * (value - min) / (max - min) - 1.0;
        return Math.clamp(normalized, -1.0, 1.0);
    }
}
