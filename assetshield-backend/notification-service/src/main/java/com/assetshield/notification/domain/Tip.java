package com.assetshield.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A tip instantiated for a user+property; text/category are snapshots. */
@Entity
@Table(name = "tips")
public class Tip {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Column(name = "tip_template_id", nullable = false, updatable = false)
    private UUID tipTemplateId;

    @Column(name = "tip_text", nullable = false, length = 600, updatable = false)
    private String tipText;

    @Column(nullable = false, length = 20, updatable = false)
    private String category;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getTipTemplateId() {
        return tipTemplateId;
    }

    public void setTipTemplateId(UUID tipTemplateId) {
        this.tipTemplateId = tipTemplateId;
    }

    public String getTipText() {
        return tipText;
    }

    public void setTipText(String tipText) {
        this.tipText = tipText;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
