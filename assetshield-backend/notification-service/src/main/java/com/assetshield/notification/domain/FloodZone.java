package com.assetshield.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "flood_zones")
public class FloodZone {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "min_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal minLat;

    @Column(name = "max_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal maxLat;

    @Column(name = "min_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal minLng;

    @Column(name = "max_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal maxLng;

    /** Bounding-box containment, edges inclusive. */
    public boolean contains(BigDecimal lat, BigDecimal lng) {
        return lat != null && lng != null
                && minLat.compareTo(lat) <= 0 && maxLat.compareTo(lat) >= 0
                && minLng.compareTo(lng) <= 0 && maxLng.compareTo(lng) >= 0;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
