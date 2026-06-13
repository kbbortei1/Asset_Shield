package com.assetshield.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Active household member (revoked_at IS NULL); can_export gates exports. */
@Entity
@Table(name = "household_memberships")
public class HouseholdMembership {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Column(name = "member_user_id", nullable = false, updatable = false)
    private UUID memberUserId;

    @Column(name = "granted_by_user_id", nullable = false, updatable = false)
    private UUID grantedByUserId;

    @Column(name = "can_export", nullable = false)
    private boolean canExport;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getMemberUserId() {
        return memberUserId;
    }

    public void setMemberUserId(UUID memberUserId) {
        this.memberUserId = memberUserId;
    }

    public UUID getGrantedByUserId() {
        return grantedByUserId;
    }

    public void setGrantedByUserId(UUID grantedByUserId) {
        this.grantedByUserId = grantedByUserId;
    }

    public boolean isCanExport() {
        return canExport;
    }

    public void setCanExport(boolean canExport) {
        this.canExport = canExport;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
