package com.assetshield.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;

/** Receipt photo attached to an asset. receipt_url holds the object path. */
@Entity
@Table(name = "asset_receipts")
public class AssetReceipt {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "receipt_url", nullable = false, length = 512, updatable = false)
    private String receiptUrl;

    // CHAR(64) in the schema; without the explicit JDBC type code Hibernate's
    // validate mode expects varchar and refuses to boot.
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "sha256_hash", nullable = false, length = 64, updatable = false)
    private String sha256Hash;

    @Column(name = "uploaded_by_user_id", nullable = false, updatable = false)
    private UUID uploadedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() {
        return id;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public String getReceiptUrl() {
        return receiptUrl;
    }

    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public UUID getUploadedByUserId() {
        return uploadedByUserId;
    }

    public void setUploadedByUserId(UUID uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
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
