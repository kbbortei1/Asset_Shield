package com.assetshield.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "dossier_shares")
public class DossierShare {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "dossier_id", nullable = false, updatable = false)
    private UUID dossierId;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "agent_interest_id", nullable = false, updatable = false)
    private UUID agentInterestId;

    @Column(name = "shared_by_user_id", nullable = false, updatable = false)
    private UUID sharedByUserId;

    @Column(name = "consent_at", nullable = false, updatable = false)
    private Instant consentAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getDossierId() {
        return dossierId;
    }

    public void setDossierId(UUID dossierId) {
        this.dossierId = dossierId;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public void setAgentId(UUID agentId) {
        this.agentId = agentId;
    }

    public UUID getAgentInterestId() {
        return agentInterestId;
    }

    public void setAgentInterestId(UUID agentInterestId) {
        this.agentInterestId = agentInterestId;
    }

    public UUID getSharedByUserId() {
        return sharedByUserId;
    }

    public void setSharedByUserId(UUID sharedByUserId) {
        this.sharedByUserId = sharedByUserId;
    }

    public Instant getConsentAt() {
        return consentAt;
    }

    public void setConsentAt(Instant consentAt) {
        this.consentAt = consentAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
