package com.assetshield.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Last maintenance reminder per asset+kind. The stored dueOn is the dedupe:
 * the sweep skips while it matches and re-reminds once the user schedules a
 * new date.
 */
@Entity
@Table(name = "maintenance_reminders")
@IdClass(MaintenanceReminder.Key.class)
public class MaintenanceReminder {

    public static class Key implements Serializable {

        private UUID assetId;
        private String kind;

        public Key() {
        }

        public Key(UUID assetId, String kind) {
            this.assetId = assetId;
            this.kind = kind;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(assetId, key.assetId) && Objects.equals(kind, key.kind);
        }

        @Override
        public int hashCode() {
            return Objects.hash(assetId, kind);
        }
    }

    @Id
    @Column(name = "asset_id")
    private UUID assetId;

    @Id
    @Column(length = 10)
    private String kind;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "reminded_at", nullable = false)
    private Instant remindedAt;

    protected MaintenanceReminder() {
    }

    public MaintenanceReminder(UUID assetId, String kind, LocalDate dueOn, Instant remindedAt) {
        this.assetId = assetId;
        this.kind = kind;
        this.dueOn = dueOn;
        this.remindedAt = remindedAt;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getKind() {
        return kind;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public void setDueOn(LocalDate dueOn) {
        this.dueOn = dueOn;
    }

    public Instant getRemindedAt() {
        return remindedAt;
    }

    public void setRemindedAt(Instant remindedAt) {
        this.remindedAt = remindedAt;
    }
}
