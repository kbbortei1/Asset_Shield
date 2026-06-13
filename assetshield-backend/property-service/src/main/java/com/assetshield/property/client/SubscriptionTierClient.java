package com.assetshield.property.client;

import java.util.UUID;

/**
 * Resolves a user's subscription tier ("FREE" | "PRO"). Backed by
 * marketplace-service from Day 5; selected by TIER_LOOKUP_MODE=stub|remote
 * (default stub until then).
 */
public interface SubscriptionTierClient {

    String FREE = "FREE";

    String tierFor(UUID userId);

    default boolean isFree(UUID userId) {
        return FREE.equalsIgnoreCase(tierFor(userId));
    }
}
