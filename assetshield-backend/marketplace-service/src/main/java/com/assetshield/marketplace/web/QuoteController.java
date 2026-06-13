package com.assetshield.marketplace.web;

import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.common.PageEnvelope;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.share.QuoteService;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.OwnerQuoteItem;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.QuoteRespondResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.RespondRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner side of policy quotes. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Quotes", description = "Owner review of agent policy quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    private static AuthUser user(Authentication authentication) {
        return (AuthUser) authentication.getPrincipal();
    }

    @Operation(summary = "Quotes issued on my connections")
    @GetMapping("/users/me/quotes")
    public ApiResponse<PageEnvelope<OwnerQuoteItem>> myQuotes(Authentication authentication,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(quoteService.ownerQuotes(user(authentication), page, size),
                "Quotes fetched");
    }

    @Operation(summary = "Accept or decline a quote (accept is the billable referral)")
    @PutMapping("/quotes/{id}/respond")
    public ApiResponse<QuoteRespondResponse> respond(Authentication authentication,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody RespondRequest request) {
        return ApiResponse.success(
                quoteService.respond(user(authentication), id, request.accept()),
                "Quote response recorded");
    }
}
