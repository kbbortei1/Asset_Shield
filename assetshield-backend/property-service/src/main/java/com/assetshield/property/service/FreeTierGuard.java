package com.assetshield.property.service;

import com.assetshield.property.client.SubscriptionTierClient;
import com.assetshield.property.common.ApiException;
import com.assetshield.property.common.ErrorCode;
import com.assetshield.property.config.AppProperties;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** FREE-tier quotas: 1 property, 30 photos per property. PRO is unlimited. */
@Component
public class FreeTierGuard {

    private final SubscriptionTierClient tierClient;
    private final AppProperties.Limits limits;

    public FreeTierGuard(SubscriptionTierClient tierClient, AppProperties properties) {
        this.tierClient = tierClient;
        this.limits = properties.limits();
    }

    public void checkPropertyQuota(UUID userId, long currentOwnedProperties) {
        if (tierClient.isFree(userId) && currentOwnedProperties >= limits.freeMaxProperties()) {
            throw new ApiException(ErrorCode.FREE_TIER_LIMIT,
                    "Free tier allows only " + limits.freeMaxProperties()
                            + " property; upgrade to PRO to add more");
        }
    }

    public void checkAssetQuota(UUID userId, long currentPropertyAssets) {
        if (tierClient.isFree(userId) && currentPropertyAssets >= limits.freeMaxAssetsPerProperty()) {
            throw new ApiException(ErrorCode.FREE_TIER_LIMIT,
                    "Free tier allows up to " + limits.freeMaxAssetsPerProperty()
                            + " photos per property; upgrade to PRO to add more");
        }
    }
}
