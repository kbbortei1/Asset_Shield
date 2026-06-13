package com.assetshield.marketplace.web;

import com.assetshield.marketplace.agent.AgentAccountService;
import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.common.PageEnvelope;
import com.assetshield.marketplace.lead.InterestService;
import com.assetshield.marketplace.lead.LeadService;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.share.ShareService;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.AgentInterestItem;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.AgentMeResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.LeadDto;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.SharedDossierItem;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.SubscriptionInitResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.SubscriptionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/me")
@Tag(name = "Agents", description = "Agent home, subscription, leads, interests, shared dossiers")
public class AgentController {

    private final AgentAccountService accountService;
    private final LeadService leadService;
    private final InterestService interestService;
    private final ShareService shareService;

    public AgentController(AgentAccountService accountService, LeadService leadService,
                           InterestService interestService, ShareService shareService) {
        this.accountService = accountService;
        this.leadService = leadService;
        this.interestService = interestService;
        this.shareService = shareService;
    }

    private static AuthUser user(Authentication authentication) {
        return (AuthUser) authentication.getPrincipal();
    }

    @Operation(summary = "Agent home: profile, verification state, subscription")
    @GetMapping
    public ApiResponse<AgentMeResponse> me(Authentication authentication) {
        return ApiResponse.success(accountService.me(user(authentication)), "Agent profile fetched");
    }

    @Operation(summary = "Current subscription (or status NONE)")
    @GetMapping("/subscription")
    public ApiResponse<SubscriptionView> subscription(Authentication authentication) {
        return ApiResponse.success(accountService.subscription(user(authentication)),
                "Subscription fetched");
    }

    @Operation(summary = "Start a monthly subscription payment")
    @PostMapping("/subscription")
    public ResponseEntity<ApiResponse<SubscriptionInitResponse>> subscribe(Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accountService.initiateSubscription(user(authentication)),
                        "Subscription payment initialized"));
    }

    @Operation(summary = "Opt-in leads — the strict five-field projection")
    @GetMapping("/leads")
    public ApiResponse<PageEnvelope<LeadDto>> leads(Authentication authentication,
                                                    @RequestParam(required = false) String propertyType,
                                                    @RequestParam(required = false) String locality,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                leadService.leads(user(authentication), propertyType, locality, page, size),
                "Leads fetched");
    }

    @Operation(summary = "My expressed interests")
    @GetMapping("/interests")
    public ApiResponse<PageEnvelope<AgentInterestItem>> interests(Authentication authentication,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(interestService.agentInterests(user(authentication), page, size),
                "Interests fetched");
    }

    @Operation(summary = "Dossiers shared with me (active consent only)")
    @GetMapping("/shared-dossiers")
    public ApiResponse<PageEnvelope<SharedDossierItem>> sharedDossiers(Authentication authentication,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(shareService.sharedDossiers(user(authentication), page, size),
                "Shared dossiers fetched");
    }
}
