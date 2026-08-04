package com.assetshield.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String internalApiKey,
        String damageServiceUri,
        String marketplaceServiceUri,
        Payments payments) {

    public record Jwt(String secret) {
    }

    public record Payments(String mode, String paystackSecretKey, String paystackBaseUrl,
                           String callbackUrl, long mockAutoSettleMs, boolean reconcileEnabled) {
    }
}
