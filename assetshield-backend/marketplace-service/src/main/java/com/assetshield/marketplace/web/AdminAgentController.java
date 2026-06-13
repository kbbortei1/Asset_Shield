package com.assetshield.marketplace.web;

import com.assetshield.marketplace.agent.AgentAdminService;
import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.common.PageEnvelope;
import com.assetshield.marketplace.domain.VerificationStatus;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.AdminAgentItem;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.VerifyAgentRequest;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.VerifyAgentResponse;
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

/** ADMIN-only (enforced by the security chain): agent verification queue. */
@RestController
@RequestMapping("/api/v1/admin/agents")
@Tag(name = "Admin · Agents", description = "Agent verification queue (ADMIN)")
public class AdminAgentController {

    private final AgentAdminService adminService;

    public AdminAgentController(AgentAdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "List agents, optionally by verification status")
    @GetMapping
    public ApiResponse<PageEnvelope<AdminAgentItem>> list(
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminService.list(status, page, size), "Agents fetched");
    }

    @Operation(summary = "Approve or reject a pending agent")
    @PutMapping("/{id}/verify")
    public ApiResponse<VerifyAgentResponse> verify(Authentication authentication,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody VerifyAgentRequest request) {
        AuthUser admin = (AuthUser) authentication.getPrincipal();
        return ApiResponse.success(adminService.verify(admin, id, request), "Agent verification recorded");
    }
}
