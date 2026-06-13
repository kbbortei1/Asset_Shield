package com.assetshield.marketplace.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Checkout provider abstraction, selected by env PAYMENTS_MODE=paystack|mock.
 */
public interface PaymentProvider {

    record InitResult(String authorizationUrl, String accessCode) {
    }

    enum VerifyStatus {
        SUCCESS, FAILED, PENDING
    }

    record VerifyResult(VerifyStatus status, String raw) {
    }

    InitResult initialize(String reference, BigDecimal amountGhs, String email, Map<String, Object> metadata);

    VerifyResult verify(String reference);

    /** GHS → pesewas (×100, exact integer): 50.00 → 5000, 49.99 → 4999. */
    static long toPesewas(BigDecimal amountGhs) {
        return amountGhs.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
