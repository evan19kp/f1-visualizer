package com.evanp.f1.ingestion.normalize;

import com.evanp.f1.core.position.NormalizedPosition;
import com.evanp.f1.core.position.SessionBounds;
import java.util.List;

public record NormalizationResult(List<NormalizedPosition> positions, SessionBounds updatedBounds) {}
