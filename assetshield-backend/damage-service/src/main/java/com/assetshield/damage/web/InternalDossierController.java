package com.assetshield.damage.web;

import com.assetshield.damage.common.ApiResponse;
import com.assetshield.damage.service.DossierService;
import com.assetshield.damage.web.dto.DossierDtos.DownloadResponse;
import com.assetshield.damage.web.dto.DossierDtos.MetaResponse;
import com.assetshield.damage.web.dto.DossierDtos.PaymentConfirmedRequest;
import com.assetshield.damage.web.dto.DossierDtos.PaymentConfirmedResponse;
import com.assetshield.damage.web.dto.DossierDtos.VerifyResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal dossier API: payment settlement callback (marketplace), meta and
 * integrity verification (Day 5 marketplace shares). X-Internal-Api-Key only.
 */
@RestController
@RequestMapping("/internal/dossiers")
public class InternalDossierController {

    private final DossierService dossierService;

    public InternalDossierController(DossierService dossierService) {
        this.dossierService = dossierService;
    }

    @PostMapping("/{id}/payment-confirmed")
    public ApiResponse<PaymentConfirmedResponse> paymentConfirmed(@PathVariable UUID id,
                                                                  @Valid @RequestBody PaymentConfirmedRequest request) {
        return ApiResponse.success(dossierService.paymentConfirmed(id, request.paymentId()),
                "Payment confirmation accepted");
    }

    @GetMapping("/{id}/meta")
    public ApiResponse<MetaResponse> meta(@PathVariable UUID id) {
        return ApiResponse.success(dossierService.meta(id), "Dossier meta fetched");
    }

    @GetMapping("/{id}/verify")
    public ApiResponse<VerifyResponse> verify(@PathVariable UUID id) {
        return ApiResponse.success(dossierService.verify(id), "Dossier verified");
    }

    /**
     * Fresh signed download URL for a dossier the caller has already authorised
     * (marketplace enforces active agent consent before calling). READY only.
     */
    @GetMapping("/{id}/signed-download")
    public ApiResponse<DownloadResponse> signedDownload(@PathVariable UUID id) {
        return ApiResponse.success(dossierService.signedDownloadForShared(id),
                "Download link issued");
    }
}
