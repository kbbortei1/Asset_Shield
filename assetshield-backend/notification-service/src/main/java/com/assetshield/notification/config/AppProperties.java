package com.assetshield.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String internalApiKey,
        String propertyServiceUri,
        Fcm fcm,
        Tips tips,
        Sched sched) {

    public record Jwt(String secret) {
    }

    public record Fcm(String mode, String firebaseServiceAccountPath) {
    }

    public record Tips(int batchSize, long debounceMinutes) {
    }

    public record Sched(String tipDeliveryCron, String redocCron, int redocStaleDays) {
    }
}
