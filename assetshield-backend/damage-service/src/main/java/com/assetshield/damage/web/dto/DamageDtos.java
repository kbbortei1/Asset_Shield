package com.assetshield.damage.web.dto;

import com.assetshield.damage.domain.DisasterType;
import com.assetshield.damage.domain.PairingMethod;
import com.assetshield.damage.domain.ReportStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DamageDtos {

    public static final String SHA256_REGEX = "^[a-f0-9]{64}$";
    public static final String SHA256_MESSAGE = "must be 64 lowercase hex characters";

    private DamageDtos() {
    }

    // ── reports ─────────────────────────────────────────────────────────────

    public record CreateReportRequest(
            @NotNull DisasterType disasterType,
            @Size(max = 1000) String description,
            @NotNull @PastOrPresent(message = "must not be in the future") Instant occurredAt) {
    }

    public record ReportCreatedResponse(UUID id, UUID propertyId, DisasterType disasterType,
                                        ReportStatus status, Instant occurredAt, Instant createdAt) {
    }

    public record ReportListItem(UUID id, DisasterType disasterType, ReportStatus status,
                                 Instant occurredAt, BigDecimal totalEstimatedLoss,
                                 long photoCount, long pairCount, Instant completedAt) {
    }

    public record MyReportItem(UUID id, UUID propertyId, DisasterType disasterType, ReportStatus status,
                               Instant occurredAt, BigDecimal totalEstimatedLoss,
                               long photoCount, long pairCount, Instant completedAt) {
    }

    public record ReportDetailResponse(UUID id, UUID propertyId, DisasterType disasterType,
                                       ReportStatus status, String description, Instant occurredAt,
                                       BigDecimal totalEstimatedLoss, Instant completedAt,
                                       List<PhotoItem> photos, List<PairItem> pairs) {
    }

    public record CompleteResponse(ReportStatus status, BigDecimal totalEstimatedLoss,
                                   long pairCount, long photoCount, Instant completedAt) {
    }

    // ── photos ──────────────────────────────────────────────────────────────

    public record PhotoMetadata(
            @NotBlank @Pattern(regexp = SHA256_REGEX, message = SHA256_MESSAGE) String sha256Hash,
            @NotNull @DecimalMin(value = "-90") @DecimalMax(value = "90") BigDecimal gpsLat,
            @NotNull @DecimalMin(value = "-180") @DecimalMax(value = "180") BigDecimal gpsLng,
            @NotNull Instant capturedAt,
            @Size(max = 500) String description) {
    }

    public record PhotoItem(UUID id, String photoUrl, String sha256Hash, BigDecimal gpsLat,
                            BigDecimal gpsLng, Instant capturedAt, String description, boolean paired) {
    }

    public record UploadedPhoto(UUID id, String photoUrl, String sha256Hash, BigDecimal gpsLat,
                                BigDecimal gpsLng, Instant capturedAt) {
    }

    public record PairingSuggestion(UUID assetId, double distanceMeters, String description,
                                    BigDecimal estimatedValue, String category, String thumbnailUrl,
                                    Instant capturedAt) {
    }

    public record PhotoUploadResponse(UploadedPhoto photo, List<PairingSuggestion> pairingSuggestions) {
    }

    public record SuggestionsResponse(List<PairingSuggestion> pairingSuggestions) {
    }

    // ── pairs ───────────────────────────────────────────────────────────────

    public record CreatePairRequest(
            @NotNull UUID damagePhotoId,
            @NotNull UUID assetId,
            @NotNull PairingMethod pairingMethod) {
    }

    /** The "before" block — built ONLY from the frozen snapshot. */
    public record BeforeBlock(String photoUrl, String sha256Hash, String description,
                              BigDecimal estimatedValue, String category, BigDecimal gpsLat,
                              BigDecimal gpsLng, Instant capturedAt) {
    }

    public record PairItem(UUID id, UUID damagePhotoId, UUID assetId, PairingMethod pairingMethod,
                           BigDecimal distanceMeters, BeforeBlock before) {
    }

    public record DeleteResponse(boolean deleted) {
    }

    // ── internal API (Day 4 PDF builder consumes this) ─────────────────────

    public record InternalPhoto(UUID id, String objectPath, String sha256Hash, BigDecimal gpsLat,
                                BigDecimal gpsLng, Instant capturedAt, String description) {
    }

    public record InternalPair(UUID id, UUID damagePhotoId, UUID assetId, PairingMethod pairingMethod,
                               BigDecimal distanceMeters, BeforeBlock before) {
    }

    public record InternalReportResponse(UUID id, UUID propertyId, UUID createdByUserId,
                                         DisasterType disasterType, ReportStatus status,
                                         String description, Instant occurredAt,
                                         BigDecimal totalEstimatedLoss, Instant completedAt,
                                         List<InternalPhoto> photos, List<InternalPair> pairs) {
    }
}
