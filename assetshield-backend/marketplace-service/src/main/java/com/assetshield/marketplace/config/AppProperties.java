package com.assetshield.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String internalApiKey,
        String damageServiceUri,
        String authServiceUri,
        String propertyServiceUri,
        Payments payments,
        Pricing pricing,
        Notifications notifications) {

    public record Jwt(String secret) {
    }

    public record Payments(String mode, String paystackSecretKey, String paystackBaseUrl,
                           long mockAutoSettleMs, boolean reconcileEnabled) {
    }

    public record Pricing(java.math.BigDecimal agentSubGhs, java.math.BigDecimal proSubGhs) {
    }

    public record Notifications(String mode) {
    }
}
