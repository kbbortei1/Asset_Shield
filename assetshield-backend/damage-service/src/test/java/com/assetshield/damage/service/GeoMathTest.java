package com.assetshield.damage.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeoMathTest {

    @Test
    void knownAccraFixtureIsAbout25MetersApart() {
        // Two points in Accra ~25 m apart (spec fixture)
        double meters = GeoMath.haversineMeters(5.546111, -0.211667, 5.546330, -0.211700);
        assertThat(meters).isBetween(20.0, 30.0);
    }

    @Test
    void zeroDistanceForIdenticalPoints() {
        assertThat(GeoMath.haversineMeters(5.546111, -0.211667, 5.546111, -0.211667)).isZero();
    }

    @Test
    void farPointsExceedThePairingRadius() {
        // ~200 m of latitude — must never appear inside a 25 m radius
        double meters = GeoMath.haversineMeters(5.5460, -0.2117, 5.5478, -0.2117);
        assertThat(meters).isGreaterThan(150.0);
    }

    @Test
    void boundingBoxContainsTheRadiusCircle() {
        GeoMath.BoundingBox box = GeoMath.boundingBox(5.5461, -0.2117, 25);
        assertThat(box.minLat()).isLessThan(5.5461);
        assertThat(box.maxLat()).isGreaterThan(5.5461);
        assertThat(box.maxLat() - box.minLat()).isGreaterThanOrEqualTo(2 * 25 / GeoMath.METERS_PER_DEGREE);
    }
}
