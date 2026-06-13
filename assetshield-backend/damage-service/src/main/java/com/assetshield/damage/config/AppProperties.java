package com.assetshield.damage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String internalApiKey,
        String propertyServiceUri,
        String marketplaceServiceUri,
        String authServiceUri,
        java.math.BigDecimal dossierFeeGhs,
        double pairingRadiusMeters,
        Storage storage) {

    public record Jwt(String secret) {
    }

    public record Storage(String provider, String localRoot, String firebaseServiceAccountPath,
                          String firebaseBucket, String supabaseUrl, String supabaseServiceKey,
                          String supabaseBucket, long signedUrlTtlMinutes) {
    }
}
