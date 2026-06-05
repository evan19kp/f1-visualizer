package com.evanp.f1.ingestion.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.SessionBounds;
import com.evanp.f1.ingestion.openf1.OpenF1LocationResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoordinateNormalizerTest {

    private static final Instant TIMESTAMP = Instant.parse("2024-03-02T15:00:00Z");
    private final CoordinateNormalizer normalizer = new CoordinateNormalizer();

    @Test
    void normalize_singlePoint_mapsToOrigin() {
        OpenF1LocationResponse sample = location(44, 10.0, 20.0, 30.0);

        NormalizationResult result = normalizer.normalize(List.of(sample), SessionBounds.empty());

        NormalizedPosition position = result.positions().getFirst();
        assertEquals(0.0, position.x());
        assertEquals(0.0, position.y());
        assertEquals(0.0, position.z());
        assertTrue(result.updatedBounds().isInitialized());
    }

    @Test
    void normalize_expandsExistingBounds() {
        SessionBounds bounds = SessionBounds.empty().expand(0.0, 0.0, 0.0);
        OpenF1LocationResponse sample = location(1, 10.0, 0.0, 0.0);

        NormalizationResult result = normalizer.normalize(List.of(sample), bounds);

        assertEquals(0.0, result.updatedBounds().minX());
        assertEquals(10.0, result.updatedBounds().maxX());
        assertEquals(1.0, result.positions().getFirst().x());
    }

    @Test
    void normalize_mapsMidpointToZero() {
        OpenF1LocationResponse low = location(1, 0.0, 0.0, 0.0);
        OpenF1LocationResponse high = location(2, 10.0, 10.0, 10.0);
        OpenF1LocationResponse mid = location(3, 5.0, 5.0, 5.0);

        NormalizationResult result = normalizer.normalize(List.of(low, high, mid), SessionBounds.empty());

        NormalizedPosition position = result.positions().get(2);
        assertEquals(0.0, position.x());
        assertEquals(0.0, position.y());
        assertEquals(0.0, position.z());
    }

    @Test
    void normalize_flatAxis_returnsZero() {
        OpenF1LocationResponse sample = location(44, 5.0, 5.0, 5.0);

        NormalizationResult result = normalizer.normalize(List.of(sample), SessionBounds.empty());

        assertEquals(0.0, result.positions().getFirst().y());
    }

    private static OpenF1LocationResponse location(int driver, double x, double y, double z) {
        return new OpenF1LocationResponse(TIMESTAMP, driver, 100L, 9161L, x, y, z);
    }
}
