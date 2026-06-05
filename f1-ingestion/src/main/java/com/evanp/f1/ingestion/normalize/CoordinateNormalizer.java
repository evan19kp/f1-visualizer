package com.evanp.f1.ingestion.normalize;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.SessionBounds;
import com.evanp.f1.ingestion.openf1.OpenF1LocationResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CoordinateNormalizer {

    public NormalizationResult normalize(List<OpenF1LocationResponse> samples, SessionBounds bounds) {
        SessionBounds updatedBounds = bounds.isInitialized() ? bounds : SessionBounds.empty();
        for (OpenF1LocationResponse sample : samples) {
            updatedBounds = updatedBounds.expand(sample.x(), sample.y(), sample.z());
        }

        List<NormalizedPosition> positions = new ArrayList<>(samples.size());
        for (OpenF1LocationResponse sample : samples) {
            positions.add(
                    new NormalizedPosition(
                            sample.driverNumber(),
                            sample.sessionKey(),
                            sample.date(),
                            normalizeAxis(sample.x(), updatedBounds.minX(), updatedBounds.maxX()),
                            normalizeAxis(sample.y(), updatedBounds.minY(), updatedBounds.maxY()),
                            normalizeAxis(sample.z(), updatedBounds.minZ(), updatedBounds.maxZ())));
        }

        return new NormalizationResult(positions, updatedBounds);
    }

    public NormalizedPosition renormalize(
            NormalizedPosition position, SessionBounds oldBounds, SessionBounds newBounds) {
        return new NormalizedPosition(
                position.driverNumber(),
                position.sessionKey(),
                position.timestamp(),
                normalizeAxis(
                        denormalizeAxis(position.x(), oldBounds.minX(), oldBounds.maxX()),
                        newBounds.minX(),
                        newBounds.maxX()),
                normalizeAxis(
                        denormalizeAxis(position.y(), oldBounds.minY(), oldBounds.maxY()),
                        newBounds.minY(),
                        newBounds.maxY()),
                normalizeAxis(
                        denormalizeAxis(position.z(), oldBounds.minZ(), oldBounds.maxZ()),
                        newBounds.minZ(),
                        newBounds.maxZ()));
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
