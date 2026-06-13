package com.assetshield.damage.domain;

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

/** Payment-gated, tamper-evident PDF dossier of a completed damage report. */
@Entity
@Table(name = "dossiers")
public class Dossier {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "damage_report_id", nullable = false, updatable = false)
    private UUID damageReportId;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private UUID requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DossierStatus status = DossierStatus.PENDING_PAYMENT;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "file_url", length = 512)
    private String fileUrl;

    // CHAR(64) in the schema; explicit JDBC type code keeps validate happy
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "manifest_hash", length = 64)
    private String manifestHash;

    @Column(name = "total_estimated_loss", precision = 12, scale = 2)
    private BigDecimal totalEstimatedLoss;

    @Column(name = "page_count")
    private Short pageCount;

    @Column(name = "share_token", nullable = false, unique = true)
    private UUID shareToken = UUID.randomUUID();

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public UUID getId() {
        return id;
    }

    public UUID getDamageReportId() {
        return damageReportId;
    }

    public void setDamageReportId(UUID damageReportId) {
        this.damageReportId = damageReportId;
    }

    public UUID getRequestedByUserId() {
        return requestedByUserId;
    }

    public void setRequestedByUserId(UUID requestedByUserId) {
        this.requestedByUserId = requestedByUserId;
    }

    public DossierStatus getStatus() {
        return status;
    }

    public void setStatus(DossierStatus status) {
        this.status = status;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getManifestHash() {
        return manifestHash;
    }

    public void setManifestHash(String manifestHash) {
        this.manifestHash = manifestHash;
    }

    public BigDecimal getTotalEstimatedLoss() {
        return totalEstimatedLoss;
    }

    public void setTotalEstimatedLoss(BigDecimal totalEstimatedLoss) {
        this.totalEstimatedLoss = totalEstimatedLoss;
    }

    public Short getPageCount() {
        return pageCount;
    }

    public void setPageCount(Short pageCount) {
        this.pageCount = pageCount;
    }

    public UUID getShareToken() {
        return shareToken;
    }

    public void setShareToken(UUID shareToken) {
        this.shareToken = shareToken;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
