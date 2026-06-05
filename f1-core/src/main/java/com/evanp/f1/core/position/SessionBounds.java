package com.evanp.f1.core.position;

public record SessionBounds(
        double minX,
        double maxX,
        double minY,
        double maxY,
        double minZ,
        double maxZ) {

    public static SessionBounds empty() {
        return new SessionBounds(
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY);
    }

    public SessionBounds expand(double x, double y, double z) {
        if (!isInitialized()) {
            return new SessionBounds(x, x, y, y, z, z);
        }
        return new SessionBounds(
                Math.min(minX, x),
                Math.max(maxX, x),
                Math.min(minY, y),
                Math.max(maxY, y),
                Math.min(minZ, z),
                Math.max(maxZ, z));
    }

    public boolean isInitialized() {
        return minX != Double.POSITIVE_INFINITY;
    }
}
