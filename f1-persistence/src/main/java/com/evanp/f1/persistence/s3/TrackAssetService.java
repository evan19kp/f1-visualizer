package com.evanp.f1.persistence.s3;

import java.net.URL;
import java.util.Optional;

public interface TrackAssetService {

    Optional<URL> getPresignedTrackUrl(String circuitSlug);
}
