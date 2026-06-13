package com.assetshield.property.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String internalApiKey,
        String authServiceUri,
        Storage storage,
        Tier tier,
        Marketplace marketplace,
        Notifications notifications,
        Events events,
        String notificationServiceUri,
        Limits limits) {

    public record Jwt(String secret) {
    }

    public record Storage(String provider, String localRoot, String firebaseServiceAccountPath,
                          String firebaseBucket, String supabaseUrl, String supabaseServiceKey,
                          String supabaseBucket, long signedUrlTtlMinutes) {
    }

    public record Tier(String mode, String stubTier, String marketplaceUri) {
    }

    public record Marketplace(String eventsMode, String uri) {
    }

    public record Notifications(String mode) {
    }

    public record Events(String mode) {
    }

    public record Limits(int freeMaxProperties, int freeMaxAssetsPerProperty) {
    }
}
