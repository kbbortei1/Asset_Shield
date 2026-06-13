package com.assetshield.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * One documented possession. photo_url holds the storage OBJECT PATH, never a
 * URL — signed URLs are minted at read time. Photo, hash, GPS and capturedAt
 * are immutable evidence; only description, value and category may change.
 */
@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "photo_url", nullable = false, length = 512, updatable = false)
    private String photoUrl;

    // CHAR(64) in the schema; without the explicit JDBC type code Hibernate's
    // validate mode expects varchar and refuses to boot.
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "sha256_hash", nullable = false, length = 64, updatable = false)
    private String sha256Hash;

    @Column(name = "gps_lat", nullable = false, precision = 9, scale = 6, updatable = false)
    private BigDecimal gpsLat;

    @Column(name = "gps_lng", nullable = false, precision = 9, scale = 6, updatable = false)
    private BigDecimal gpsLng;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "estimated_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetCategory category;

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

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
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

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getEstimatedValue() {
        return estimatedValue;
    }

    public void setEstimatedValue(BigDecimal estimatedValue) {
        this.estimatedValue = estimatedValue;
    }

    public AssetCategory getCategory() {
        return category;
    }

    public void setCategory(AssetCategory category) {
        this.category = category;
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
