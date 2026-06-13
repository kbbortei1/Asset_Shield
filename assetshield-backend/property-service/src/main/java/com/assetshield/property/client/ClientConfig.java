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
        String mode = properties.notifications().mode();
        if (!"log".equals(mode)) {
            throw new IllegalStateException(
                    "Unknown NOTIFICATIONS_MODE '" + mode + "' (only 'log' exists until Day 6)");
        }
        return new LogNotificationClient();
    }
}
