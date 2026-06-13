package com.assetshield.marketplace.client;

import com.assetshield.marketplace.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Env-selected implementations for not-yet-built downstream services. */
@Configuration
public class ClientConfig {

    @Bean
    public NotificationClient notificationClient(AppProperties properties) {
        String mode = properties.notifications().mode();
        if (!"log".equals(mode)) {
            throw new IllegalStateException(
                    "Unknown NOTIFICATIONS_MODE '" + mode + "' (only 'log' exists until Day 6)");
        }
        return new LogNotificationClient();
    }
}
