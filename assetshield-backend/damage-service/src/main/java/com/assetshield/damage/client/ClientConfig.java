package com.assetshield.damage.client;

import com.assetshield.damage.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Env-selected notification client (log | remote). */
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
