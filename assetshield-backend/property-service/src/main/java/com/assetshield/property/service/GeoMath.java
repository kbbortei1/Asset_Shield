package com.assetshield.property.service;

/** Haversine distance + bounding-box prefilter math for assets-near queries. */
public final class GeoMath {

    public static final double EARTH_RADIUS_M = 6_371_000.0;
    /** Metres per degree of latitude (and of longitude at the equator). */
    public static final double METERS_PER_DEGREE = 111_320.0;

    /** minLat, maxLat, minLng, maxLng around a centre point. */
    public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
    }

    private GeoMath() {
    }

    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    /**
     * Degrees ≈ radiusM / 111320 for latitude; longitude degrees shrink by
     * cos(lat). Clamped to valid ranges so polar/antimeridian inputs cannot
     * produce an invalid box.
     */
    public static BoundingBox boundingBox(double lat, double lng, double radiusM) {
        double latDelta = radiusM / METERS_PER_DEGREE;
        double cosLat = Math.max(Math.cos(Math.toRadians(lat)), 1e-6);
        double lngDelta = radiusM / (METERS_PER_DEGREE * cosLat);
        return new BoundingBox(
                Math.max(lat - latDelta, -90), Math.min(lat + latDelta, 90),
                Math.max(lng - lngDelta, -180), Math.min(lng + lngDelta, 180));
    }
}
