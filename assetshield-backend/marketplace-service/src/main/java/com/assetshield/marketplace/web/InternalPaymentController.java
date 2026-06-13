package com.assetshield.marketplace.web;

import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.payment.PaymentService;
import com.assetshield.marketplace.web.dto.PaymentDtos.InitializeRequest;
import com.assetshield.marketplace.web.dto.PaymentDtos.InitializeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service payment initialization (damage-service dossier fees
 * today; subscriptions Day 5). Never routed by the gateway; guarded by the
 * X-Internal-Api-Key header.
 */
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private final PaymentService paymentService;

    public InternalPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<InitializeResponse>> initialize(
            @Valid @RequestBody InitializeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(paymentService.initialize(request), "Payment initialized"));
    }
}
