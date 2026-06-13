package com.assetshield.damage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;

/**
 * One "after" photo. photo_url holds the storage OBJECT PATH, never a URL —
 * signed URLs are minted at read time. All fields are immutable evidence.
 */
@Entity
@Table(name = "damage_photos")
public class DamagePhoto {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "damage_report_id", nullable = false, updatable = false)
    private UUID damageReportId;

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

    @Column(length = 500, updatable = false)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() {
        return id;
    }

    public UUID getDamageReportId() {
        return damageReportId;
    }

    public void setDamageReportId(UUID damageReportId) {
        this.damageReportId = damageReportId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
