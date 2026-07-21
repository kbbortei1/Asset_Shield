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
        String notificationServiceUri,
        Superadmin superadmin,
        Storage storage) {

    public record Jwt(String secret, long accessTtlSeconds) {
    }

    public record Storage(String provider, String localRoot, String s3Endpoint, String s3Region,
                          String s3AccessKeyId, String s3SecretAccessKey, String bucket,
                          long signedUrlTtlMinutes) {
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
