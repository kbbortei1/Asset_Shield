package com.assetshield.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Invitation to join a property's household. invitee_user_id stays null until
 * the phone number resolves to a registered user (at invite time or accept
 * time).
 */
@Entity
@Table(name = "household_invitations")
public class HouseholdInvitation {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Column(name = "invited_by_user_id", nullable = false, updatable = false)
    private UUID invitedByUserId;

    @Column(name = "invitee_phone", nullable = false, length = 16, updatable = false)
    private String inviteePhone;

    @Column(name = "invitee_user_id")
    private UUID inviteeUserId;

    @Column(name = "can_export", nullable = false)
    private boolean canExport;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    public void setInvitedByUserId(UUID invitedByUserId) {
        this.invitedByUserId = invitedByUserId;
    }

    public String getInviteePhone() {
        return inviteePhone;
    }

    public void setInviteePhone(String inviteePhone) {
        this.inviteePhone = inviteePhone;
    }

    public UUID getInviteeUserId() {
        return inviteeUserId;
    }

    public void setInviteeUserId(UUID inviteeUserId) {
        this.inviteeUserId = inviteeUserId;
    }

    public boolean isCanExport() {
        return canExport;
    }

    public void setCanExport(boolean canExport) {
        this.canExport = canExport;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
