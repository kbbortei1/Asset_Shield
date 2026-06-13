package com.assetshield.marketplace.client;

import com.assetshield.marketplace.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Env-selected implementations for not-yet-built downstream services. */
@Configuration
public class ClientConfig {

    @Bean
    public NotificationClient notificationClient(AppProperties properties) {
        return switch (properties.notifications().mode()) {
            case "log" -> new LogNotificationClient();
            case "remote" -> new RemoteNotificationClient(properties.notificationServiceUri(),
                    properties.internalApiKey());
            default -> throw new IllegalStateException(
                    "Unknown NOTIFICATIONS_MODE '" + properties.notifications().mode()
                            + "' (expected log|remote)");
        };
    }
}
