package com.assetshield.property.client;

import com.assetshield.property.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Env-selected implementations for not-yet-built downstream services. */
@Configuration
public class ClientConfig {

    @Bean
    public SubscriptionTierClient subscriptionTierClient(AppProperties properties) {
        AppProperties.Tier tier = properties.tier();
        return switch (tier.mode()) {
            case "stub" -> new StubSubscriptionTierClient(tier.stubTier());
            case "remote" -> new RemoteSubscriptionTierClient(tier.marketplaceUri(), properties.internalApiKey());
            default -> throw new IllegalStateException(
                    "Unknown TIER_LOOKUP_MODE '" + tier.mode() + "' (expected stub|remote)");
        };
    }

    @Bean
    public MarketplaceEventsClient marketplaceEventsClient(AppProperties properties) {
        AppProperties.Marketplace marketplace = properties.marketplace();
        return switch (marketplace.eventsMode()) {
            case "log" -> new LogMarketplaceEventsClient();
            case "remote" -> new RemoteMarketplaceEventsClient(marketplace.uri(),
                    properties.internalApiKey());
            default -> throw new IllegalStateException(
                    "Unknown MARKETPLACE_EVENTS_MODE '" + marketplace.eventsMode()
                            + "' (expected log|remote)");
        };
    }

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

    @Bean
    public EventPublisher eventPublisher(AppProperties properties) {
        return switch (properties.events().mode()) {
            case "log" -> new LogEventPublisher();
            case "remote" -> new RemoteEventPublisher(properties.notificationServiceUri(),
                    properties.internalApiKey());
            default -> throw new IllegalStateException(
                    "Unknown EVENTS_MODE '" + properties.events().mode() + "' (expected log|remote)");
        };
    }
}
