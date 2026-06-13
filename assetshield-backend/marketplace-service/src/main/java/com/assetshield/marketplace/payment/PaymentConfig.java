package com.assetshield.marketplace.payment;

import com.assetshield.marketplace.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects the checkout provider by env PAYMENTS_MODE=paystack|mock. */
@Configuration
public class PaymentConfig {

    @Bean
    public PaymentProvider paymentProvider(AppProperties properties,
                                           ObjectProvider<PaymentSettlementService> settlementService) {
        AppProperties.Payments payments = properties.payments();
        return switch (payments.mode()) {
            case "paystack" -> new PaystackProvider(payments.paystackBaseUrl(), payments.paystackSecretKey());
            case "mock" -> new MockProvider(settlementService, payments.mockAutoSettleMs());
            default -> throw new IllegalStateException(
                    "Unknown PAYMENTS_MODE '" + payments.mode() + "' (expected paystack|mock)");
        };
    }
}
