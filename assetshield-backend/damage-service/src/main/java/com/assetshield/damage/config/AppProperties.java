package com.assetshield.damage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String internalApiKey,
        String propertyServiceUri,
        String paymentServiceUri,
        String authServiceUri,
        java.math.BigDecimal dossierFeeGhs,
        double pairingRadiusMeters,
        Notifications notifications,
        String notificationServiceUri,
        Storage storage) {

    public record Jwt(String secret) {
    }

    public record Notifications(String mode) {
    }

    public record Storage(String provider, String localRoot, String s3Endpoint, String s3Region,
                          String s3AccessKeyId, String s3SecretAccessKey, String bucket,
                          long signedUrlTtlMinutes) {
    }
}
