package com.assetshield.marketplace.subscription;

import com.assetshield.marketplace.client.PaymentClient;
import com.assetshield.marketplace.common.ApiException;
import com.assetshield.marketplace.common.ErrorCode;
import com.assetshield.marketplace.config.AppProperties;
import com.assetshield.marketplace.domain.PaymentPurpose;
import com.assetshield.marketplace.domain.SubscriptionStatus;
import com.assetshield.marketplace.domain.UserSubscription;
import com.assetshield.marketplace.repo.UserSubscriptionRepository;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.SubscriptionInitResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.TierResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner PRO subscription initiation and read models (the tier endpoint that
 * property-service's free-tier limits call). State transitions live in
 * {@link SubscriptionSettlementService}.
 */
@Service
public class SubscriptionService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final PaymentClient paymentClient;
    private final AppProperties properties;

    public SubscriptionService(UserSubscriptionRepository userSubscriptionRepository,
                               PaymentClient paymentClient, AppProperties properties) {
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.paymentClient = paymentClient;
        this.properties = properties;
    }

    @Transactional
    public SubscriptionInitResponse initiatePro(AuthUser user) {
        if (!"OWNER".equals(user.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "PRO subscriptions are for owner accounts");
        }
        PaymentClient.PaymentInit init = paymentClient.initialize(
                user.id(), user.phone(), PaymentPurpose.PRO_SUBSCRIPTION.name(),
                properties.pricing().proSubGhs(), user.id());
        return new SubscriptionInitResponse(init.paymentId(), init.reference(),
                properties.pricing().proSubGhs(), "GHS", init.authorizationUrl());
    }

    /** FREE is the absence of an ACTIVE unexpired PRO row. */
    @Transactional(readOnly = true)
    public Map<String, Object> mySubscription(AuthUser user) {
        UserSubscription active = activeUnexpiredPro(user.id());
        Map<String, Object> view = new LinkedHashMap<>();
        if (active != null) {
            view.put("tier", "PRO");
            view.put("status", "ACTIVE");
            view.put("expiresAt", active.getExpiresAt());
            view.put("limits", null);
        } else {
            view.put("tier", "FREE");
            view.put("limits", Map.of("maxProperties", 1, "maxPhotosPerProperty", 30));
        }
        return view;
    }

    /** Internal read model behind property-service's free-tier limits. */
    @Transactional(readOnly = true)
    public TierResponse tier(UUID userId) {
        return new TierResponse(activeUnexpiredPro(userId) != null ? "PRO" : "FREE");
    }

    private UserSubscription activeUnexpiredPro(UUID userId) {
        return userSubscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .filter(sub -> sub.getExpiresAt().isAfter(Instant.now()))
                .orElse(null);
    }
}
