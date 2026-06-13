package com.assetshield.marketplace.web;

import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.share.QuoteService;
import com.assetshield.marketplace.share.ShareService;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.DossierVerifyView;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.QuoteCreateRequest;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.QuoteCreateResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.RevokeShareResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.ShareRequest;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.ShareResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Marketplace's slice of /dossiers/{id}: owner consent (share/revoke) and the
 * agent's consent-gated reads (verify, quote). Everything else under
 * /dossiers/** is damage-service — the gateway splits the paths.
 */
@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}")
@Tag(name = "Dossier sharing", description = "Owner-consented dossier shares, verification and quotes")
public class DossierShareController {

    private final ShareService shareService;
    private final QuoteService quoteService;

    public DossierShareController(ShareService shareService, QuoteService quoteService) {
        this.shareService = shareService;
        this.quoteService = quoteService;
    }

    private static AuthUser user(Authentication authentication) {
        return (AuthUser) authentication.getPrincipal();
    }

    @Operation(summary = "Share a READY dossier with an accepted agent (owner consent)")
    @PostMapping("/share-to-agent")
    public ResponseEntity<ApiResponse<ShareResponse>> share(Authentication authentication,
                                                            @PathVariable UUID dossierId,
                                                            @Valid @RequestBody ShareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        shareService.share(user(authentication), dossierId, request.agentInterestId()),
                        "Dossier shared"));
    }

    @Operation(summary = "Revoke a share (the connection stays accepted)")
    @DeleteMapping("/share-to-agent/{agentId}")
    public ApiResponse<RevokeShareResponse> revokeShare(Authentication authentication,
                                                        @PathVariable UUID dossierId,
                                                        @PathVariable UUID agentId) {
        return ApiResponse.success(
                shareService.revokeShare(user(authentication), dossierId, agentId),
                "Share revoked");
    }

    @Operation(summary = "Verify dossier integrity (agent, active consent required)")
    @GetMapping("/verify")
    public ApiResponse<DossierVerifyView> verify(Authentication authentication,
                                                 @PathVariable UUID dossierId) {
        return ApiResponse.success(quoteService.verify(user(authentication), dossierId),
                "Dossier verified");
    }

    @Operation(summary = "Issue a policy quote on a shared dossier (agent)")
    @PostMapping("/quote")
    public ResponseEntity<ApiResponse<QuoteCreateResponse>> quote(Authentication authentication,
                                                                  @PathVariable UUID dossierId,
                                                                  @Valid @RequestBody QuoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        quoteService.create(user(authentication), dossierId, request),
                        "Quote issued"));
    }
}
