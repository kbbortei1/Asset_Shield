package com.assetshield.marketplace.web;

import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.subscription.SubscriptionSettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settlement callback from payment-service: a subscription payment succeeded.
 * Guarded by X-Internal-Api-Key; idempotent (re-dispatch of the same payment
 * is a no-op via last_payment_id).
 */
@RestController
@RequestMapping("/internal/subscriptions")
public class InternalSubscriptionController {

    public record PaymentConfirmedRequest(
            @NotBlank String purpose,
            @NotNull UUID referenceEntityId,
            @NotNull UUID paymentId) {
    }

    private final SubscriptionSettlementService settlementService;

    public InternalSubscriptionController(SubscriptionSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/payment-confirmed")
    public ApiResponse<Map<String, Boolean>> paymentConfirmed(
            @Valid @RequestBody PaymentConfirmedRequest request) {
        switch (request.purpose()) {
            case "AGENT_SUBSCRIPTION" ->
                    settlementService.activateAgentSubscription(request.referenceEntityId(), request.paymentId());
            case "PRO_SUBSCRIPTION" ->
                    settlementService.activateProSubscription(request.referenceEntityId(), request.paymentId());
            default -> throw new IllegalArgumentException("Unknown subscription purpose: " + request.purpose());
        }
        return ApiResponse.success(Map.of("applied", true), "Subscription settlement applied");
    }
}
