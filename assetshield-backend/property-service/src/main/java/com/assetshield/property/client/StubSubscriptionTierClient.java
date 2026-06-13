package com.assetshield.property.client;

import java.util.UUID;

/** TIER_LOOKUP_MODE=stub: every user gets the STUB_TIER env value. */
public class StubSubscriptionTierClient implements SubscriptionTierClient {

    private final String stubTier;

    public StubSubscriptionTierClient(String stubTier) {
        this.stubTier = stubTier;
    }

    @Override
    public String tierFor(UUID userId) {
        return stubTier;
    }
}
