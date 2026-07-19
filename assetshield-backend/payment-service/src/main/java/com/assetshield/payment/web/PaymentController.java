package com.assetshield.payment.web;

import com.assetshield.payment.common.ApiResponse;
import com.assetshield.payment.common.PageEnvelope;
import com.assetshield.payment.service.PaymentService;
import com.assetshield.payment.security.AuthUser;
import com.assetshield.payment.web.dto.PaymentDtos.PaymentDetails;
import com.assetshield.payment.web.dto.PaymentDtos.PaymentSummary;
import com.assetshield.payment.web.dto.PaymentDtos.VerifyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Payments", description = "Payment status, billing history and post-checkout verification (payer only)")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Verify a payment with the provider and settle on success")
    @PostMapping("/payments/{reference}/verify")
    public ApiResponse<VerifyResponse> verify(Authentication authentication,
                                              @PathVariable String reference) {
        AuthUser user = (AuthUser) authentication.getPrincipal();
        return ApiResponse.success(paymentService.verify(user.id(), reference), "Payment verified");
    }

    @Operation(summary = "Payment details (payer only)")
    @GetMapping("/payments/{reference}")
    public ApiResponse<PaymentDetails> details(Authentication authentication,
                                               @PathVariable String reference) {
        AuthUser user = (AuthUser) authentication.getPrincipal();
        return ApiResponse.success(paymentService.details(user.id(), reference), "Payment fetched");
    }

    @Operation(summary = "The caller's billing history, newest first")
    @GetMapping("/users/me/payments")
    public ApiResponse<PageEnvelope<PaymentSummary>> history(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        AuthUser user = (AuthUser) authentication.getPrincipal();
        return ApiResponse.success(
                PageEnvelope.of(paymentService.history(user.id(), PageEnvelope.clampPage(page),
                        PageEnvelope.clampSize(size))),
                "Payments fetched");
    }
}
