package com.evanp.f1.api.dto;

import com.evanp.f1.core.position.SessionBounds;

public record SessionBoundsDto(
        double minX,
        double maxX,
        double minY,
        double maxY,
        double minZ,
        double maxZ) {

    public static SessionBoundsDto from(SessionBounds bounds) {
        return new SessionBoundsDto(
                bounds.minX(),
                bounds.maxX(),
                bounds.minY(),
                bounds.maxY(),
                bounds.minZ(),
                bounds.maxZ());
    }
}
