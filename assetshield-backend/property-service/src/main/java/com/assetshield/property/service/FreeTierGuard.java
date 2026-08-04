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

    /**
     * Quota is measured in PHOTOS per property. Adding a multi-photo asset must
     * not push the property past the cap, so we check the post-add total.
     */
    public void checkPhotoQuota(UUID userId, long currentPropertyPhotos, int adding) {
        if (tierClient.isFree(userId)
                && currentPropertyPhotos + adding > limits.freeMaxAssetsPerProperty()) {
            throw new ApiException(ErrorCode.FREE_TIER_LIMIT,
                    "Free tier allows up to " + limits.freeMaxAssetsPerProperty()
                            + " photos per property; upgrade to PRO to add more");
        }
    }
}
