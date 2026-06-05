package com.evanp.f1.core.position;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionBoundsTest {

    @Test
    void empty_isNotInitialized() {
        SessionBounds bounds = SessionBounds.empty();

        assertFalse(bounds.isInitialized());
    }

    @Test
    void expand_fromEmpty_initializesToPoint() {
        SessionBounds bounds = SessionBounds.empty().expand(1.0, 2.0, 3.0);

        assertTrue(bounds.isInitialized());
        assertEquals(1.0, bounds.minX());
        assertEquals(1.0, bounds.maxX());
        assertEquals(2.0, bounds.minY());
        assertEquals(2.0, bounds.maxY());
        assertEquals(3.0, bounds.minZ());
        assertEquals(3.0, bounds.maxZ());
    }

    @Test
    void expand_growsBounds() {
        SessionBounds bounds = SessionBounds.empty()
                .expand(0.0, 0.0, 0.0)
                .expand(10.0, -5.0, 2.5);

        assertEquals(0.0, bounds.minX());
        assertEquals(10.0, bounds.maxX());
        assertEquals(-5.0, bounds.minY());
        assertEquals(0.0, bounds.maxY());
        assertEquals(0.0, bounds.minZ());
        assertEquals(2.5, bounds.maxZ());
    }
}
