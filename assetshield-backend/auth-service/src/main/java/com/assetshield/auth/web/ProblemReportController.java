package com.assetshield.auth.web;

import com.assetshield.auth.common.ApiResponse;
import com.assetshield.auth.service.ProblemReportService;
import com.assetshield.auth.web.dto.AuthDtos.CreateReportRequest;
import com.assetshield.auth.web.dto.AuthDtos.ReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/problem-reports")
@Tag(name = "Support", description = "In-app problem reports")
public class ProblemReportController {

    private final ProblemReportService reportService;

    public ProblemReportController(ProblemReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Report a problem you're facing in the app")
    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponse>> create(Authentication authentication,
                                                              @Valid @RequestBody CreateReportRequest request) {
        UUID id = reportService.create((UUID) authentication.getPrincipal(),
                request.category(), request.message(), request.context());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new ReportResponse(id, "OPEN"), "Report submitted"));
    }
}
