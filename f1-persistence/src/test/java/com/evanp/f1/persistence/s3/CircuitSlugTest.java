package com.evanp.f1.persistence.s3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CircuitSlugTest {

    @Test
    void fromCircuitName_lowercasesAndHyphenates() {
        assertThat(CircuitSlug.fromCircuitName("Bahrain")).isEqualTo("bahrain");
    }

    @Test
    void fromCircuitName_replacesNonAlphanumericWithHyphens() {
        assertThat(CircuitSlug.fromCircuitName("São Paulo")).isEqualTo("s-o-paulo");
        assertThat(CircuitSlug.fromCircuitName("Yas Marina")).isEqualTo("yas-marina");
    }

    @Test
    void fromCircuitName_trimsLeadingAndTrailingHyphens() {
        assertThat(CircuitSlug.fromCircuitName("  Bahrain  ")).isEqualTo("bahrain");
        assertThat(CircuitSlug.fromCircuitName("--Test--")).isEqualTo("test");
    }

    @Test
    void fromCircuitName_returnsEmptyForBlank() {
        assertThat(CircuitSlug.fromCircuitName(null)).isEmpty();
        assertThat(CircuitSlug.fromCircuitName("")).isEmpty();
        assertThat(CircuitSlug.fromCircuitName("   ")).isEmpty();
    }
}
