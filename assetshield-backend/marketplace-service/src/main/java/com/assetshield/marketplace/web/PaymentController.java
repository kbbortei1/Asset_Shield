package com.assetshield.marketplace.web;

import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.payment.PaymentService;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.PaymentDtos.PaymentDetails;
import com.assetshield.marketplace.web.dto.PaymentDtos.VerifyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment status and post-checkout verification (payer only)")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Verify a payment with the provider and settle on success")
    @PostMapping("/{reference}/verify")
    public ApiResponse<VerifyResponse> verify(Authentication authentication,
                                              @PathVariable String reference) {
        AuthUser user = (AuthUser) authentication.getPrincipal();
        return ApiResponse.success(paymentService.verify(user.id(), reference), "Payment verified");
    }

    @Operation(summary = "Payment details (payer only)")
    @GetMapping("/{reference}")
    public ApiResponse<PaymentDetails> details(Authentication authentication,
                                               @PathVariable String reference) {
        AuthUser user = (AuthUser) authentication.getPrincipal();
        return ApiResponse.success(paymentService.details(user.id(), reference), "Payment fetched");
    }
}
