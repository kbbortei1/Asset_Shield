package com.assetshield.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** One message in an owner<->agent thread, scoped to an accepted interest. */
@Entity
@Table(name = "interest_messages")
public class InterestMessage {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "agent_interest_id", nullable = false, updatable = false)
    private UUID agentInterestId;

    @Column(name = "sender_user_id", nullable = false, updatable = false)
    private UUID senderUserId;

    @Column(name = "sender_role", nullable = false, length = 10, updatable = false)
    private String senderRole;

    @Column(nullable = false, length = 2000, updatable = false)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getAgentInterestId() {
        return agentInterestId;
    }

    public void setAgentInterestId(UUID agentInterestId) {
        this.agentInterestId = agentInterestId;
    }

    public UUID getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(UUID senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public void setSenderRole(String senderRole) {
        this.senderRole = senderRole;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
