package com.assetshield.marketplace.web.dto;

import com.assetshield.marketplace.domain.PaymentPurpose;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record InitializeRequest(
            @NotNull UUID userId,
            @NotBlank String userPhone,
            @NotNull PaymentPurpose purpose,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amountGhs,
            @NotNull UUID referenceEntityId) {
    }

    public record InitializeResponse(UUID paymentId, String reference, String authorizationUrl) {
    }

    public record VerifyResponse(String reference, String status, String purpose) {
    }

    public record PaymentDetails(String reference, String purpose, BigDecimal amount,
                                 String currency, String status, Instant createdAt) {
    }
}
