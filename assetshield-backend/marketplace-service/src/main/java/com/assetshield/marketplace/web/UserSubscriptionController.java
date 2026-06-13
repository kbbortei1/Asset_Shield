package com.assetshield.marketplace.web;

import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.subscription.SubscriptionService;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.SubscriptionInitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner PRO subscription: purchase and current tier/limits. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "PRO subscription", description = "Owner PRO tier purchase and status")
public class UserSubscriptionController {

    private final SubscriptionService subscriptionService;

    public UserSubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    private static AuthUser user(Authentication authentication) {
        return (AuthUser) authentication.getPrincipal();
    }

    @Operation(summary = "Start a PRO subscription payment (owners)")
    @PostMapping("/subscriptions/pro")
    public ResponseEntity<ApiResponse<SubscriptionInitResponse>> subscribePro(Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(subscriptionService.initiatePro(user(authentication)),
                        "Subscription payment initialized"));
    }

    @Operation(summary = "My tier: PRO with expiry, or FREE with limits")
    @GetMapping("/users/me/subscription")
    public ApiResponse<Map<String, Object>> mySubscription(Authentication authentication) {
        return ApiResponse.success(subscriptionService.mySubscription(user(authentication)),
                "Subscription fetched");
    }
}
