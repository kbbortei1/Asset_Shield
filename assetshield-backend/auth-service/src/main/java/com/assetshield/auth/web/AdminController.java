package com.assetshield.auth.web;

import com.assetshield.auth.common.ApiResponse;
import com.assetshield.auth.common.PageEnvelope;
import com.assetshield.auth.service.AdminBroadcastService;
import com.assetshield.auth.service.AuditService;
import com.assetshield.auth.service.ProblemReportService;
import com.assetshield.auth.service.UserService;
import com.assetshield.auth.web.dto.AuthDtos.AdminUserItem;
import com.assetshield.auth.web.dto.AuthDtos.AudienceCountsResponse;
import com.assetshield.auth.web.dto.AuthDtos.AuditEventItem;
import com.assetshield.auth.web.dto.AuthDtos.BroadcastRequest;
import com.assetshield.auth.web.dto.AuthDtos.BroadcastResponse;
import com.assetshield.auth.web.dto.AuthDtos.CreateAdminRequest;
import com.assetshield.auth.web.dto.AuthDtos.CreateAdminResponse;
import com.assetshield.auth.web.dto.AuthDtos.ReportItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final ProblemReportService reportService;
    private final AdminBroadcastService broadcastService;

    public AdminController(UserService userService, AuditService auditService,
                          ProblemReportService reportService, AdminBroadcastService broadcastService) {
        this.userService = userService;
        this.auditService = auditService;
        this.reportService = reportService;
        this.broadcastService = broadcastService;
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

    @Operation(summary = "User-filed problem reports (optional ?status=OPEN|RESOLVED)")
    @GetMapping("/problem-reports")
    public ApiResponse<PageEnvelope<ReportItem>> reports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(reportService.list(status, page, size), "Reports fetched");
    }

    @Operation(summary = "Mark a problem report resolved")
    @PutMapping("/problem-reports/{id}/resolve")
    public ApiResponse<Map<String, Boolean>> resolveReport(Authentication authentication,
                                                           @PathVariable UUID id) {
        reportService.resolve((UUID) authentication.getPrincipal(), id);
        return ApiResponse.success(Map.of("resolved", true), "Report resolved");
    }

    @Operation(summary = "Reach per broadcast segment (everyone / owners / agents)")
    @GetMapping("/audience-counts")
    public ApiResponse<AudienceCountsResponse> audienceCounts() {
        return ApiResponse.success(broadcastService.counts(), "Counts fetched");
    }

    @Operation(summary = "Search the user directory for the 'specific people' picker")
    @GetMapping("/users")
    public ApiResponse<PageEnvelope<AdminUserItem>> users(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(broadcastService.search(q, page, size), "Users fetched");
    }

    @Operation(summary = "Broadcast an in-app notification to a segment or specific people")
    @PostMapping("/broadcast")
    public ApiResponse<BroadcastResponse> broadcast(@Valid @RequestBody BroadcastRequest request) {
        int reach = broadcastService.broadcast(request.audience(), request.userIds(),
                request.title(), request.body(), request.inApp(), request.push());
        return ApiResponse.success(new BroadcastResponse(true, reach), "Broadcast sent");
    }
}
