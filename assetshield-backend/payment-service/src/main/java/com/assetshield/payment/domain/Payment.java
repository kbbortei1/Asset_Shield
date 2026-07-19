package com.assetshield.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25, updatable = false)
    private PaymentPurpose purpose;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    // CHAR(3) in the schema; explicit JDBC type code keeps validate happy
    @JdbcTypeCode(Types.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "GHS";

    @Column(nullable = false, length = 20, updatable = false)
    private String provider = "PAYSTACK";

    @Column(name = "provider_reference", nullable = false, length = 100, updatable = false, unique = true)
    private String providerReference;

    @Column(name = "reference_entity_id", nullable = false, updatable = false)
    private UUID referenceEntityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(name = "webhook_received_at")
    private Instant webhookReceivedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_webhook")
    private String rawWebhook;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public PaymentPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(PaymentPurpose purpose) {
        this.purpose = purpose;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public UUID getReferenceEntityId() {
        return referenceEntityId;
    }

    public void setReferenceEntityId(UUID referenceEntityId) {
        this.referenceEntityId = referenceEntityId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public Instant getWebhookReceivedAt() {
        return webhookReceivedAt;
    }

    public void setWebhookReceivedAt(Instant webhookReceivedAt) {
        this.webhookReceivedAt = webhookReceivedAt;
    }

    public String getRawWebhook() {
        return rawWebhook;
    }

    public void setRawWebhook(String rawWebhook) {
        this.rawWebhook = rawWebhook;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
