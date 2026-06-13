package com.assetshield.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "properties")
public class Property {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropertyType type;

    @Column(name = "gps_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal gpsLat;

    @Column(name = "gps_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal gpsLng;

    @Column(nullable = false, length = 120)
    private String locality;

    @Column(name = "open_to_offers", nullable = false)
    private boolean openToOffers;

    @Column(name = "open_to_offers_at")
    private Instant openToOffersAt;

    @Column(name = "last_documented_at")
    private Instant lastDocumentedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PropertyType getType() {
        return type;
    }

    public void setType(PropertyType type) {
        this.type = type;
    }

    public BigDecimal getGpsLat() {
        return gpsLat;
    }

    public void setGpsLat(BigDecimal gpsLat) {
        this.gpsLat = gpsLat;
    }

    public BigDecimal getGpsLng() {
        return gpsLng;
    }

    public void setGpsLng(BigDecimal gpsLng) {
        this.gpsLng = gpsLng;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public boolean isOpenToOffers() {
        return openToOffers;
    }

    public void setOpenToOffers(boolean openToOffers) {
        this.openToOffers = openToOffers;
    }

    public Instant getOpenToOffersAt() {
        return openToOffersAt;
    }

    public void setOpenToOffersAt(Instant openToOffersAt) {
        this.openToOffersAt = openToOffersAt;
    }

    public Instant getLastDocumentedAt() {
        return lastDocumentedAt;
    }

    public void setLastDocumentedAt(Instant lastDocumentedAt) {
        this.lastDocumentedAt = lastDocumentedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public long getVersion() {
        return version;
    }
}
