package com.assetshield.damage.web.dto;

import com.assetshield.damage.domain.DisasterType;
import com.assetshield.damage.domain.DossierStatus;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DossierDtos {

    private DossierDtos() {
    }

    public record PaymentBlock(UUID paymentId, BigDecimal amount, String currency,
                               String reference, String authorizationUrl) {
    }

    public record GenerateResponse(UUID dossierId, DossierStatus status, PaymentBlock payment) {
    }

    public record StatusResponse(UUID dossierId, DossierStatus status, BigDecimal totalEstimatedLoss,
                                 Short pageCount, String manifestHash, Instant generatedAt,
                                 String failureReason) {
    }

    public record DownloadResponse(String downloadUrl, String fileName) {
    }

    public record SharedResponse(String downloadUrl, String propertyName, DisasterType disasterType,
                                 Instant generatedAt, String manifestHash) {
    }

    public record RotateResponse(UUID shareToken) {
    }

    public record MyDossierItem(UUID id, UUID damageReportId, String propertyName,
                                DisasterType disasterType, DossierStatus status,
                                BigDecimal totalEstimatedLoss, Instant generatedAt) {
    }

    // ── internal API ────────────────────────────────────────────────────────

    public record PaymentConfirmedRequest(@NotNull UUID paymentId) {
    }

    public record PaymentConfirmedResponse(boolean accepted) {
    }

    public record MetaResponse(UUID id, UUID requestedByUserId, UUID propertyId, UUID damageReportId,
                               DossierStatus status, String manifestHash, String fileUrl,
                               BigDecimal totalEstimatedLoss, DisasterType disasterType,
                               Instant generatedAt) {
    }

    public record Mismatch(String objectPath, String expected, String actual) {
    }

    /** tamperEvident semantics: true = intact, false = tampering detected. */
    public record VerifyResponse(String manifestHash, String recomputedHash, boolean tamperEvident,
                                 int photoCount, List<Mismatch> mismatches) {
    }
}
