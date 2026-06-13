package com.assetshield.marketplace.web;

import com.assetshield.marketplace.agent.AgentSyncService;
import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.subscription.SubscriptionService;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.AgentSyncRequest;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.AgentSyncResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.OptInChangedRequest;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.TierResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service marketplace API. Never routed by the gateway; reachable
 * only on the internal Docker network and guarded by X-Internal-Api-Key.
 */
@RestController
@RequestMapping("/internal")
public class InternalMarketplaceController {

    private final AgentSyncService agentSyncService;
    private final SubscriptionService subscriptionService;

    public InternalMarketplaceController(AgentSyncService agentSyncService,
                                         SubscriptionService subscriptionService) {
        this.agentSyncService = agentSyncService;
        this.subscriptionService = subscriptionService;
    }

    /** auth-service push on agent OTP completion (and its 60 s re-push job). */
    @PostMapping("/agents/sync")
    public ApiResponse<AgentSyncResponse> sync(@Valid @RequestBody AgentSyncRequest request) {
        return ApiResponse.success(agentSyncService.sync(request), "Agent synced");
    }

    /** property-service push when an owner toggles open_to_offers. */
    @PostMapping("/marketplace/optin-changed")
    public ApiResponse<Map<String, Boolean>> optInChanged(@Valid @RequestBody OptInChangedRequest request) {
        agentSyncService.optInChanged(request.propertyId(), request.openToOffers());
        return ApiResponse.success(Map.of("accepted", true), "Opt-in change processed");
    }

    /** property-service free-tier limit lookups. */
    @GetMapping("/users/{userId}/tier")
    public ApiResponse<TierResponse> tier(@PathVariable UUID userId) {
        return ApiResponse.success(subscriptionService.tier(userId), "Tier resolved");
    }
}
