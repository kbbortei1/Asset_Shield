package com.assetshield.marketplace.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MarketplaceDtos {

    private MarketplaceDtos() {
    }

    // ── admin ────────────────────────────────────────────────────────────────

    public record AdminAgentItem(UUID agentId, UUID userId, String fullName, String phoneNumber,
                                 String insurerName, String nicLicenceNo, String verificationStatus,
                                 Instant createdAt) {
    }

    public record VerifyAgentRequest(
            @NotNull Boolean approve,
            @Size(max = 500) String rejectionReason) {
    }

    public record VerifyAgentResponse(UUID agentId, String verificationStatus, Instant verifiedAt,
                                      String rejectionReason) {
    }

    // ── agent side ───────────────────────────────────────────────────────────

    public record SubscriptionBrief(String status, Instant expiresAt) {
    }

    public record AgentMeResponse(UUID agentId, String insurerName, String nicLicenceNo,
                                  String verificationStatus, String rejectionReason,
                                  SubscriptionBrief subscription) {
    }

    public record SubscriptionInitResponse(UUID paymentId, String reference, BigDecimal amount,
                                           String currency, String authorizationUrl) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubscriptionView(String status, String plan, Instant startedAt, Instant expiresAt) {

        public static SubscriptionView none() {
            return new SubscriptionView("NONE", null, null, null);
        }
    }

    /**
     * P0 projection strictness: EXACTLY these five fields, built only from
     * the property internal lead projection. Adding anything (GPS, owner
     * phone, asset data, value) is a privacy violation.
     */
    public record LeadDto(UUID propertyId, String ownerDisplayName, String propertyName,
                          String propertyType, String locality) {
    }

    public record ExpressInterestResponse(UUID interestId, String status) {
    }

    public record AgentInterestItem(UUID interestId, String propertyName, String locality,
                                    String status, Instant createdAt, Instant respondedAt,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) String ownerFullName) {
    }

    public record SharedDossierItem(UUID dossierId, UUID shareId, UUID agentInterestId,
                                    String ownerName, String propertyName, String disasterType,
                                    BigDecimal totalEstimatedLoss, Instant sharedAt) {
    }

    public record MismatchView(String objectPath, String expected, String actual) {
    }

    public record DossierVerifyView(UUID dossierId, String manifestHash, String recomputedHash,
                                    boolean tamperEvident, int photoCount,
                                    List<MismatchView> mismatches, Instant verifiedAt) {
    }

    public record QuoteCreateRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal coverageAmount,
            @NotNull @DecimalMin(value = "0.01") BigDecimal premium,
            @NotNull @Min(1) @Max(60) Integer termMonths) {
    }

    public record QuoteCreateResponse(UUID quoteId, String status) {
    }

    // ── owner side ───────────────────────────────────────────────────────────

    public record OwnerInterestItem(UUID interestId, String agentName, String insurerName,
                                    String propertyName, String status, Instant createdAt,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) String nicLicenceNo) {
    }

    public record RespondRequest(@NotNull Boolean accept) {
    }

    public record InterestRespondResponse(UUID interestId, String status, Instant respondedAt) {
    }

    public record RevokeInterestResponse(UUID interestId, String status, long revokedShares) {
    }

    public record ShareRequest(@NotNull UUID agentInterestId) {
    }

    public record ShareResponse(UUID shareId, Instant consentAt) {
    }

    public record RevokeShareResponse(UUID shareId, Instant revokedAt) {
    }

    public record OwnerQuoteItem(UUID quoteId, String agentName, String insurerName,
                                 String propertyName, BigDecimal coverageAmount, BigDecimal premium,
                                 int termMonths, String status, Instant createdAt) {
    }

    public record QuoteRespondResponse(UUID quoteId, String status, Instant respondedAt) {
    }

    // ── internal ─────────────────────────────────────────────────────────────

    public record AgentSyncRequest(
            @NotNull UUID userId,
            @NotBlank @Size(max = 120) String insurerName,
            @NotBlank @Size(max = 50) String nicLicenceNo) {
    }

    public record AgentSyncResponse(UUID agentId, String verificationStatus) {
    }

    public record OptInChangedRequest(@NotNull UUID propertyId, @NotNull Boolean openToOffers) {
    }

    public record TierResponse(String tier) {
    }

    // ── owner<->agent chat ───────────────────────────────────────────────────

    public record SendMessageRequest(@NotBlank @Size(max = 2000) String body) {
    }

    public record MessageItem(UUID id, UUID senderUserId, String senderRole, String body,
                              Instant createdAt) {
    }
}
