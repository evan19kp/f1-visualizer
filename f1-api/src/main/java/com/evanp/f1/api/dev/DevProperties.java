package com.evanp.f1.api.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.dev")
public record DevProperties(boolean enabled, String trackMeshRoot) {

    public DevProperties {
        if (trackMeshRoot == null || trackMeshRoot.isBlank()) {
            trackMeshRoot = "tools/track-mesh";
        }
    }
}
