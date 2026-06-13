package com.assetshield.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Last re-documentation reminder per property (suppression window). */
@Entity
@Table(name = "redoc_reminders")
public class RedocReminder {

    @Id
    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "reminded_at", nullable = false)
    private Instant remindedAt;

    protected RedocReminder() {
    }

    public RedocReminder(UUID propertyId, Instant remindedAt) {
        this.propertyId = propertyId;
        this.remindedAt = remindedAt;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public Instant getRemindedAt() {
        return remindedAt;
    }

    public void setRemindedAt(Instant remindedAt) {
        this.remindedAt = remindedAt;
    }
}
