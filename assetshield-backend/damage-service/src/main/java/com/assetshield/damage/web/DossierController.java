package com.assetshield.damage.web;

import com.assetshield.damage.common.ApiResponse;
import com.assetshield.damage.common.PageEnvelope;
import com.assetshield.damage.service.DossierService;
import com.assetshield.damage.web.dto.DossierDtos.DownloadResponse;
import com.assetshield.damage.web.dto.DossierDtos.GenerateResponse;
import com.assetshield.damage.web.dto.DossierDtos.MyDossierItem;
import com.assetshield.damage.web.dto.DossierDtos.RotateResponse;
import com.assetshield.damage.web.dto.DossierDtos.SharedResponse;
import com.assetshield.damage.web.dto.DossierDtos.StatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dossiers", description = "Payment-gated, tamper-evident PDF evidence dossiers")
public class DossierController {

    private final DossierService dossierService;

    public DossierController(DossierService dossierService) {
        this.dossierService = dossierService;
    }

    @Operation(summary = "Request a dossier for a completed report (starts the fee checkout)")
    @PostMapping("/api/v1/damage-reports/{id}/generate-dossier")
    public ResponseEntity<ApiResponse<GenerateResponse>> generate(Authentication authentication,
                                                                  @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        dossierService.requestDossier(DamageReportController.principal(authentication), id),
                        "Dossier requested — complete the payment to start generation"));
    }

    @Operation(summary = "Dossier status (client polls this after paying)")
    @GetMapping("/api/v1/dossiers/{id}/status")
    public ApiResponse<StatusResponse> status(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(
                dossierService.status(DamageReportController.principal(authentication), id),
                "Dossier status fetched");
    }

    @Operation(summary = "Signed download URL (READY only; 402 before payment)")
    @GetMapping("/api/v1/dossiers/{id}/download")
    public ApiResponse<DownloadResponse> download(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(
                dossierService.download(DamageReportController.principal(authentication), id),
                "Download link issued");
    }

    @Operation(summary = "Public share link (no auth; READY dossiers only)")
    @GetMapping("/api/v1/dossiers/shared/{shareToken}")
    public ApiResponse<SharedResponse> shared(@PathVariable UUID shareToken) {
        return ApiResponse.success(dossierService.shared(shareToken), "Shared dossier fetched");
    }

    @Operation(summary = "Rotate the share token (kills any leaked link)")
    @PostMapping("/api/v1/dossiers/{id}/rotate-share-token")
    public ApiResponse<RotateResponse> rotate(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(
                dossierService.rotateShareToken(DamageReportController.principal(authentication), id),
                "Share token rotated");
    }

    @Operation(summary = "Retry generation (FAILED + paid only)")
    @PostMapping("/api/v1/dossiers/{id}/retry-generation")
    public ApiResponse<StatusResponse> retry(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(
                dossierService.retry(DamageReportController.principal(authentication), id),
                "Generation restarted");
    }

    @Operation(summary = "Dossiers I requested")
    @GetMapping("/api/v1/users/me/dossiers")
    public ApiResponse<PageEnvelope<MyDossierItem>> myDossiers(Authentication authentication,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                dossierService.myDossiers(DamageReportController.principal(authentication), page, size),
                "Dossiers fetched");
    }
}
