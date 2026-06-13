package com.assetshield.notification.web;

import com.assetshield.notification.common.ApiResponse;
import com.assetshield.notification.common.PageEnvelope;
import com.assetshield.notification.security.AuthUser;
import com.assetshield.notification.service.TipService;
import com.assetshield.notification.web.dto.NotificationDtos.TipItem;
import com.assetshield.notification.web.dto.NotificationDtos.TipReadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Tips", description = "Ghana-specific safety tips feed")
public class TipController {

    private final TipService tipService;

    public TipController(TipService tipService) {
        this.tipService = tipService;
    }

    private static AuthUser user(Authentication authentication) {
        return (AuthUser) authentication.getPrincipal();
    }

    @Operation(summary = "My tips, newest first")
    @GetMapping("/tips/feed")
    public ApiResponse<PageEnvelope<TipItem>> feed(Authentication authentication,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(tipService.feed(user(authentication), page, size), "Tips fetched");
    }

    @Operation(summary = "Tips for one property (any household member)")
    @GetMapping("/properties/{id}/tips")
    public ApiResponse<PageEnvelope<TipItem>> forProperty(Authentication authentication,
                                                          @PathVariable UUID id,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(tipService.forProperty(user(authentication), id, page, size),
                "Tips fetched");
    }

    @Operation(summary = "Mark a tip read (idempotent)")
    @PutMapping("/tips/{id}/read")
    public ApiResponse<TipReadResponse> markRead(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(tipService.markRead(user(authentication), id), "Tip marked read");
    }
}
