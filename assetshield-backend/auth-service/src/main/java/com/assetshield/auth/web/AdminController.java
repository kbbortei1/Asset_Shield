package com.assetshield.auth.web;

import com.assetshield.auth.common.ApiResponse;
import com.assetshield.auth.common.PageEnvelope;
import com.assetshield.auth.service.AuditService;
import com.assetshield.auth.service.UserService;
import com.assetshield.auth.web.dto.AuthDtos.AuditEventItem;
import com.assetshield.auth.web.dto.AuthDtos.CreateAdminRequest;
import com.assetshield.auth.web.dto.AuthDtos.CreateAdminResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrator management (ADMIN role only)")
public class AdminController {

    private final UserService userService;
    private final AuditService auditService;

    public AdminController(UserService userService, AuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    @Operation(summary = "Create another admin (ACTIVE immediately, no OTP)")
    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<CreateAdminResponse>> createAdmin(
            Authentication authentication, @Valid @RequestBody CreateAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        userService.createAdmin((UUID) authentication.getPrincipal(), request),
                        "Admin created"));
    }

    @Operation(summary = "Security audit trail, newest first (optional ?action= filter)")
    @GetMapping("/audit-events")
    public ApiResponse<PageEnvelope<AuditEventItem>> auditEvents(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(auditService.list(action, page, size), "Audit events fetched");
    }
}
