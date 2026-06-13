package com.assetshield.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "policy_quotes")
public class PolicyQuote {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "agent_interest_id", nullable = false, updatable = false)
    private UUID agentInterestId;

    @Column(name = "dossier_share_id", nullable = false, updatable = false)
    private UUID dossierShareId;

    @Column(name = "coverage_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal coverageAmount;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal premium;

    @Column(name = "term_months", nullable = false, updatable = false)
    private short termMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status = QuoteStatus.PENDING;

    @Column(name = "responded_at")
    private Instant respondedAt;

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

    public UUID getDossierShareId() {
        return dossierShareId;
    }

    public void setDossierShareId(UUID dossierShareId) {
        this.dossierShareId = dossierShareId;
    }

    public BigDecimal getCoverageAmount() {
        return coverageAmount;
    }

    public void setCoverageAmount(BigDecimal coverageAmount) {
        this.coverageAmount = coverageAmount;
    }

    public BigDecimal getPremium() {
        return premium;
    }

    public void setPremium(BigDecimal premium) {
        this.premium = premium;
    }

    public short getTermMonths() {
        return termMonths;
    }

    public void setTermMonths(short termMonths) {
        this.termMonths = termMonths;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public void setStatus(QuoteStatus status) {
        this.status = status;
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
