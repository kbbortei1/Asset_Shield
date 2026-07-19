package com.assetshield.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String internalApiKey,
        String damageServiceUri,
        String authServiceUri,
        String propertyServiceUri,
        String notificationServiceUri,
        String paymentServiceUri,
        Pricing pricing,
        Notifications notifications) {

    public record Jwt(String secret) {
    }

    public record Pricing(java.math.BigDecimal agentSubGhs, java.math.BigDecimal proSubGhs) {
    }

    public record Notifications(String mode) {
    }
}
