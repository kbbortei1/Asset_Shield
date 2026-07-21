package com.assetshield.marketplace.web;

import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.common.PageEnvelope;
import com.assetshield.marketplace.domain.InterestStatus;
import com.assetshield.marketplace.lead.InterestService;
import com.assetshield.marketplace.lead.MessageService;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.InterestRespondResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.MessageItem;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.OwnerInterestItem;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.RespondRequest;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.RevokeInterestResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.SendMessageRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner side of agent connections: list, accept/decline, revoke, and chat. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Agent interests", description = "Owner responses to agent interest")
public class InterestController {

    private final InterestService interestService;
    private final MessageService messageService;

    public InterestController(InterestService interestService, MessageService messageService) {
        this.interestService = interestService;
        this.messageService = messageService;
    }

    private static AuthUser user(Authentication authentication) {
        return (AuthUser) authentication.getPrincipal();
    }

    @Operation(summary = "Agent interests on my properties")
    @GetMapping("/users/me/agent-interests")
    public ApiResponse<PageEnvelope<OwnerInterestItem>> myInterests(
            Authentication authentication,
            @RequestParam(required = false) InterestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                interestService.ownerInterests(user(authentication), status, page, size),
                "Interests fetched");
    }

    @Operation(summary = "Accept or decline a pending interest")
    @PutMapping("/agent-interests/{id}/respond")
    public ApiResponse<InterestRespondResponse> respond(Authentication authentication,
                                                        @PathVariable UUID id,
                                                        @Valid @RequestBody RespondRequest request) {
        return ApiResponse.success(
                interestService.respond(user(authentication), id, request.accept()),
                "Interest response recorded");
    }

    @Operation(summary = "Revoke an accepted connection (cascades to dossier shares)")
    @DeleteMapping("/agent-interests/{id}")
    public ApiResponse<RevokeInterestResponse> revoke(Authentication authentication,
                                                      @PathVariable UUID id) {
        return ApiResponse.success(interestService.revoke(user(authentication), id),
                "Connection revoked");
    }

    @Operation(summary = "Messages in an accepted owner<->agent conversation, oldest first")
    @GetMapping("/agent-interests/{id}/messages")
    public ApiResponse<PageEnvelope<MessageItem>> messages(Authentication authentication,
                                                           @PathVariable UUID id,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(messageService.list(user(authentication), id, page, size),
                "Messages fetched");
    }

    @Operation(summary = "Send a message to the other party in an accepted connection")
    @PostMapping("/agent-interests/{id}/messages")
    public ResponseEntity<ApiResponse<MessageItem>> sendMessage(Authentication authentication,
                                                                @PathVariable UUID id,
                                                                @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageService.send(user(authentication), id, request.body()),
                        "Message sent"));
    }
}
