package com.assetshield.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tips_frequency", nullable = false, length = 10)
    private TipsFrequency tipsFrequency = TipsFrequency.WEEKLY;

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

    public TipsFrequency getTipsFrequency() {
        return tipsFrequency;
    }

    public void setTipsFrequency(TipsFrequency tipsFrequency) {
        this.tipsFrequency = tipsFrequency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
