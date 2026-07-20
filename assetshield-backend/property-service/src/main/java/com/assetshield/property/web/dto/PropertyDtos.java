package com.assetshield.property.web.dto;

import com.assetshield.property.domain.AssetCategory;
import com.assetshield.property.domain.PropertyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PropertyDtos {

    public static final String PHONE_REGEX = "^\\+233\\d{9}$";
    public static final String PHONE_MESSAGE = "must match +233XXXXXXXXX";
    public static final String SHA256_REGEX = "^[a-f0-9]{64}$";
    public static final String SHA256_MESSAGE = "must be 64 lowercase hex characters";
    public static final String MAX_VALUE = "10000000";

    private PropertyDtos() {
    }

    // ── properties ──────────────────────────────────────────────────────────

    public record CreatePropertyRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull PropertyType type,
            @NotNull @DecimalMin(value = "-90") @DecimalMax(value = "90") BigDecimal gpsLat,
            @NotNull @DecimalMin(value = "-180") @DecimalMax(value = "180") BigDecimal gpsLng,
            @NotBlank @Size(max = 120) String locality) {
    }

    public record UpdatePropertyRequest(
            @Size(min = 1, max = 120) String name,
            PropertyType type,
            @DecimalMin(value = "-90") @DecimalMax(value = "90") BigDecimal gpsLat,
            @DecimalMin(value = "-180") @DecimalMax(value = "180") BigDecimal gpsLng,
            @Size(min = 1, max = 120) String locality) {
    }

    public record PropertyResponse(UUID id, String name, PropertyType type, String locality,
                                   BigDecimal gpsLat, BigDecimal gpsLng, boolean openToOffers,
                                   Instant openToOffersAt, Instant lastDocumentedAt,
                                   long assetCount, BigDecimal totalEstimatedValue,
                                   String myAccess, Instant createdAt) {
    }

    public record PropertyListItem(UUID id, String name, PropertyType type, String locality,
                                   BigDecimal gpsLat, BigDecimal gpsLng, boolean openToOffers,
                                   long assetCount, BigDecimal totalEstimatedValue,
                                   Instant lastDocumentedAt, String myAccess) {
    }

    public record CategoryLine(AssetCategory category, long count, BigDecimal value) {
    }

    public record Dashboard(long assetCount, BigDecimal totalEstimatedValue, List<CategoryLine> byCategory) {
    }

    public record PropertyDetailResponse(UUID id, String name, PropertyType type, String locality,
                                         BigDecimal gpsLat, BigDecimal gpsLng, boolean openToOffers,
                                         Instant openToOffersAt, Instant lastDocumentedAt,
                                         String myAccess, Instant createdAt, Dashboard dashboard) {
    }

    public record DeleteResponse(boolean deleted) {
    }

    public record OptInRequest(@NotNull Boolean openToOffers) {
    }

    public record OptInResponse(boolean openToOffers, Instant openToOffersAt) {
    }

    // ── assets ──────────────────────────────────────────────────────────────

    public record AssetMetadata(
            @NotBlank @Pattern(regexp = SHA256_REGEX, message = SHA256_MESSAGE) String sha256Hash,
            @NotNull @DecimalMin(value = "-90") @DecimalMax(value = "90") BigDecimal gpsLat,
            @NotNull @DecimalMin(value = "-180") @DecimalMax(value = "180") BigDecimal gpsLng,
            @NotNull Instant capturedAt,
            @NotBlank @Size(max = 500) String description,
            @NotNull @DecimalMin(value = "0") @DecimalMax(value = MAX_VALUE) BigDecimal estimatedValue,
            @NotNull AssetCategory category,
            LocalDate warrantyExpiresOn,
            LocalDate nextServiceOn) {
    }

    /**
     * duplicateWarning is only computed on creation (null on reads): true when
     * the same photo already documents an asset on ANOTHER property — advisory
     * fraud signal, the upload still succeeds.
     */
    public record AssetResponse(UUID id, UUID propertyId, String photoUrl, String sha256Hash,
                                BigDecimal gpsLat, BigDecimal gpsLng, Instant capturedAt,
                                String description, BigDecimal estimatedValue, AssetCategory category,
                                LocalDate warrantyExpiresOn, LocalDate nextServiceOn,
                                long receiptCount, UUID createdByUserId, Instant createdAt,
                                Boolean duplicateWarning) {
    }

    public record ReceiptItem(UUID id, String receiptUrl, Instant createdAt) {
    }

    public record AssetDetailResponse(UUID id, UUID propertyId, String photoUrl, String sha256Hash,
                                      BigDecimal gpsLat, BigDecimal gpsLng, Instant capturedAt,
                                      String description, BigDecimal estimatedValue, AssetCategory category,
                                      LocalDate warrantyExpiresOn, LocalDate nextServiceOn,
                                      UUID createdByUserId, Instant createdAt, List<ReceiptItem> receipts) {
    }

    public record UpdateAssetRequest(
            @Size(min = 1, max = 500) String description,
            @DecimalMin(value = "0") @DecimalMax(value = MAX_VALUE) BigDecimal estimatedValue,
            AssetCategory category,
            LocalDate warrantyExpiresOn,
            LocalDate nextServiceOn) {
    }

    public record ReceiptMetadata(
            @NotBlank @Pattern(regexp = SHA256_REGEX, message = SHA256_MESSAGE) String sha256Hash) {
    }

    public record ReceiptResponse(UUID id, UUID assetId, String receiptUrl, Instant createdAt) {
    }

    // ── household ───────────────────────────────────────────────────────────

    public record InviteRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String inviteePhone,
            @NotNull Boolean canExport) {
    }

    public record InviteResponse(UUID invitationId, String status, Instant expiresAt,
                                 boolean inviteeRegistered) {
    }

    public record MyInvitationItem(UUID id, String propertyName, String ownerName,
                                   boolean canExport, Instant expiresAt) {
    }

    public record RespondRequest(@NotNull Boolean accept) {
    }

    public record RespondResponse(String status, UUID membershipId) {
    }

    public record MemberItem(UUID membershipId, UUID userId, String fullName, String phoneNumber,
                             boolean canExport, Instant createdAt) {
    }

    // ── internal API ────────────────────────────────────────────────────────

    public record InternalPropertyResponse(UUID id, UUID ownerUserId, String name, PropertyType type,
                                           String locality, boolean openToOffers, boolean deleted) {
    }

    public record AccessResponse(String access) {
    }

    public record InternalAssetResponse(UUID id, UUID propertyId, String objectPath, String sha256Hash,
                                        BigDecimal gpsLat, BigDecimal gpsLng, Instant capturedAt,
                                        String description, BigDecimal estimatedValue,
                                        AssetCategory category, UUID createdByUserId, Instant createdAt) {
    }

    public record AssetNearItem(UUID assetId, double distanceMeters, String description,
                                BigDecimal estimatedValue, AssetCategory category, String thumbnailUrl,
                                String sha256Hash, Instant capturedAt) {
    }

    /** The ONLY projection the marketplace may ever consume — exactly six fields. */
    public record LeadViewResponse(UUID propertyId, String ownerDisplayName, String propertyName,
                                   PropertyType propertyType, String locality, boolean openToOffers) {
    }

    /** Lead-list item: the lead view minus the (always-true) opt-in flag. */
    public record LeadListItem(UUID propertyId, String ownerDisplayName, String propertyName,
                               PropertyType propertyType, String locality) {
    }

    /** Rule-engine input for notification-service's tips engine (Day 6). */
    public record TipsContextResponse(UUID propertyId, UUID ownerUserId, PropertyType propertyType,
                                      BigDecimal gpsLat, BigDecimal gpsLng,
                                      List<CategoryLine> byCategory) {
    }

    /** Stale-documentation sweep item (Day 6 redoc reminders). */
    public record StaleDocumentationItem(UUID propertyId, UUID ownerUserId, String name,
                                         Instant lastDocumentedAt) {
    }

    /** Maintenance sweep item for notification-service's reminder cron. */
    public record MaintenanceDueItem(UUID assetId, UUID propertyId, String propertyName,
                                     UUID ownerUserId, String description, String kind,
                                     LocalDate dueOn) {
    }

    // ── insights (timeline, analytics) ──────────────────────────────────────

    /** One derived history event; assetId is null for property-level events. */
    public record TimelineEvent(String type, Instant at, UUID assetId, String label) {
    }

    public record AnalyticsPropertyLine(UUID propertyId, String name, long assetCount,
                                        BigDecimal totalValue) {
    }

    /** Cross-property portfolio rollup for the analytics dashboard. */
    public record AssetAnalyticsResponse(long propertyCount, long assetCount, BigDecimal totalValue,
                                         List<CategoryLine> byCategory,
                                         List<AnalyticsPropertyLine> byProperty) {
    }
}
