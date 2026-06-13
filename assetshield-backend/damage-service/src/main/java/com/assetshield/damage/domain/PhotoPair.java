package com.assetshield.damage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Before/after link. asset_snapshot freezes the asset's state at pairing time
 * (JSONB) — the dossier must stay reproducible even if the asset is later
 * edited or soft-deleted in property-service. Nothing re-reads the live asset
 * after pairing.
 */
@Entity
@Table(name = "photo_pairs")
public class PhotoPair {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "damage_report_id", nullable = false, updatable = false)
    private UUID damageReportId;

    @Column(name = "damage_photo_id", nullable = false, updatable = false)
    private UUID damagePhotoId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "asset_snapshot", nullable = false, updatable = false)
    private String assetSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "pairing_method", nullable = false, length = 10, updatable = false)
    private PairingMethod pairingMethod;

    @Column(name = "distance_meters", precision = 8, scale = 2, updatable = false)
    private BigDecimal distanceMeters;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getDamageReportId() {
        return damageReportId;
    }

    public void setDamageReportId(UUID damageReportId) {
        this.damageReportId = damageReportId;
    }

    public UUID getDamagePhotoId() {
        return damagePhotoId;
    }

    public void setDamagePhotoId(UUID damagePhotoId) {
        this.damagePhotoId = damagePhotoId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public String getAssetSnapshot() {
        return assetSnapshot;
    }

    public void setAssetSnapshot(String assetSnapshot) {
        this.assetSnapshot = assetSnapshot;
    }

    public PairingMethod getPairingMethod() {
        return pairingMethod;
    }

    public void setPairingMethod(PairingMethod pairingMethod) {
        this.pairingMethod = pairingMethod;
    }

    public BigDecimal getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(BigDecimal distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
