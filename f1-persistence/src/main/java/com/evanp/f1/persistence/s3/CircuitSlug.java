package com.evanp.f1.persistence.s3;

import java.util.Locale;

public final class CircuitSlug {

    private CircuitSlug() {}

    public static String fromCircuitName(String circuitName) {
        if (circuitName == null || circuitName.isBlank()) {
            return "";
        }
        return circuitName
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
