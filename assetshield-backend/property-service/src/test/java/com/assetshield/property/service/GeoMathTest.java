package com.assetshield.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoMathTest {

    @Test
    void zeroDistanceForIdenticalPoints() {
        assertThat(GeoMath.haversineMeters(5.6037, -0.1870, 5.6037, -0.1870)).isZero();
    }

    @Test
    void knownDistanceAccraToKumasi() {
        // Accra (5.6037, -0.1870) → Kumasi (6.6885, -1.6244) ≈ 198–203 km
        double meters = GeoMath.haversineMeters(5.6037, -0.1870, 6.6885, -1.6244);
        assertThat(meters).isBetween(195_000.0, 205_000.0);
    }

    @Test
    void smallDistancesAreAccurateAtAssetScale() {
        // ~0.0001° latitude ≈ 11.13 m
        double meters = GeoMath.haversineMeters(5.6037, -0.1870, 5.6038, -0.1870);
        assertThat(meters).isCloseTo(11.13, within(0.2));
    }

    @Test
    void boundingBoxContainsTheExactRadiusCircle() {
        double lat = 5.6037;
        double lng = -0.1870;
        double radius = 25;
        GeoMath.BoundingBox box = GeoMath.boundingBox(lat, lng, radius);

        // Walk the circle: every point within the radius must be inside the box.
        for (int deg = 0; deg < 360; deg += 15) {
            double bearing = Math.toRadians(deg);
            double pointLat = lat + (radius / GeoMath.METERS_PER_DEGREE) * Math.cos(bearing);
            double pointLng = lng + (radius / (GeoMath.METERS_PER_DEGREE
                    * Math.cos(Math.toRadians(lat)))) * Math.sin(bearing);
            assertThat(pointLat).isBetween(box.minLat(), box.maxLat());
            assertThat(pointLng).isBetween(box.minLng(), box.maxLng());
        }
    }

    @Test
    void boundingBoxWidensWithLatitude() {
        double nearEquator = GeoMath.boundingBox(0, 0, 25).maxLng();
        double nordic = GeoMath.boundingBox(60, 0, 25).maxLng();
        // At 60°N a degree of longitude is half as long → the box must be ~2x wider.
        assertThat(nordic / nearEquator).isCloseTo(2.0, within(0.05));
    }

    @Test
    void boundingBoxIsClampedToValidCoordinates() {
        GeoMath.BoundingBox box = GeoMath.boundingBox(89.9999, 179.9999, 50_000);
        assertThat(box.maxLat()).isLessThanOrEqualTo(90);
        assertThat(box.maxLng()).isLessThanOrEqualTo(180);
        assertThat(box.minLng()).isGreaterThanOrEqualTo(-180);
    }
}
