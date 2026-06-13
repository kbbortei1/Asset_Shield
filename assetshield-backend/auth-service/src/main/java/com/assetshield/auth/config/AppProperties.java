package com.assetshield.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Refresh refresh,
        Otp otp,
        Sms sms,
        String internalApiKey,
        String marketplaceServiceUri,
        Superadmin superadmin,
        Storage storage) {

    public record Jwt(String secret, long accessTtlSeconds) {
    }

    public record Storage(String provider, String localRoot, String firebaseServiceAccountPath,
                          String firebaseBucket, String supabaseUrl, String supabaseServiceKey,
                          String supabaseBucket, long signedUrlTtlMinutes) {
    }

    public record Refresh(long ttlDays) {
    }

    public record Otp(long ttlSeconds, int maxAttempts, long resendIntervalSeconds, String devCode) {
    }

    public record Sms(String provider) {
    }

    public record Superadmin(String phone, String password) {
    }
}
