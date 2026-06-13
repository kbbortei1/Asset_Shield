package com.assetshield.damage.web;

import com.assetshield.damage.common.ApiResponse;
import com.assetshield.damage.common.PageEnvelope;
import com.assetshield.damage.security.AuthUser;
import com.assetshield.damage.service.DamagePhotoService;
import com.assetshield.damage.service.DamageReportService;
import com.assetshield.damage.service.PairService;
import com.assetshield.damage.web.dto.DamageDtos.CompleteResponse;
import com.assetshield.damage.web.dto.DamageDtos.CreatePairRequest;
import com.assetshield.damage.web.dto.DamageDtos.CreateReportRequest;
import com.assetshield.damage.web.dto.DamageDtos.DeleteResponse;
import com.assetshield.damage.web.dto.DamageDtos.MyReportItem;
import com.assetshield.damage.web.dto.DamageDtos.PairItem;
import com.assetshield.damage.web.dto.DamageDtos.PhotoMetadata;
import com.assetshield.damage.web.dto.DamageDtos.PhotoUploadResponse;
import com.assetshield.damage.web.dto.DamageDtos.ReportCreatedResponse;
import com.assetshield.damage.web.dto.DamageDtos.ReportDetailResponse;
import com.assetshield.damage.web.dto.DamageDtos.ReportListItem;
import com.assetshield.damage.web.dto.DamageDtos.SuggestionsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Damage reports", description = "After-disaster evidence: reports, photos, GPS pairing, loss")
public class DamageReportController {

    private final DamageReportService reportService;
    private final DamagePhotoService photoService;
    private final PairService pairService;
    private final MetadataParser metadataParser;

    public DamageReportController(DamageReportService reportService, DamagePhotoService photoService,
                                  PairService pairService, MetadataParser metadataParser) {
        this.reportService = reportService;
        this.photoService = photoService;
        this.pairService = pairService;
        this.metadataParser = metadataParser;
    }

    @Operation(summary = "Open a damage report on a property (owner / export member)")
    @PostMapping("/api/v1/properties/{propertyId}/damage-reports")
    public ResponseEntity<ApiResponse<ReportCreatedResponse>> create(Authentication authentication,
                                                                     @PathVariable UUID propertyId,
                                                                     @Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        reportService.create(principal(authentication), propertyId, request),
                        "Damage report created"));
    }

    @Operation(summary = "List a property's damage reports")
    @GetMapping("/api/v1/properties/{propertyId}/damage-reports")
    public ApiResponse<PageEnvelope<ReportListItem>> listForProperty(Authentication authentication,
                                                                     @PathVariable UUID propertyId,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                reportService.listForProperty(principal(authentication), propertyId, page, size),
                "Damage reports fetched");
    }

    @Operation(summary = "Damage reports I created")
    @GetMapping("/api/v1/users/me/damage-reports")
    public ApiResponse<PageEnvelope<MyReportItem>> myReports(Authentication authentication,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                reportService.myReports(principal(authentication), page, size),
                "Damage reports fetched");
    }

    @Operation(summary = "Full report detail: photos + pairs with frozen before-blocks")
    @GetMapping("/api/v1/damage-reports/{id}")
    public ApiResponse<ReportDetailResponse> detail(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(reportService.detail(principal(authentication), id),
                "Damage report fetched");
    }

    @Operation(summary = "Upload a damage photo (multipart file + metadata) — returns GPS pairing suggestions")
    @PostMapping(value = "/api/v1/damage-reports/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PhotoUploadResponse>> addPhoto(Authentication authentication,
                                                                     @PathVariable UUID id,
                                                                     @RequestPart("file") MultipartFile file,
                                                                     @RequestPart("metadata") String metadata) {
        PhotoMetadata parsed = metadataParser.parse(metadata, PhotoMetadata.class);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        photoService.addPhoto(principal(authentication), id, file, parsed),
                        "Damage photo captured"));
    }

    @Operation(summary = "Re-run pairing suggestions for a photo (?radiusM= override, max 200)")
    @GetMapping("/api/v1/damage-reports/{id}/photos/{photoId}/pairing-suggestions")
    public ApiResponse<SuggestionsResponse> pairingSuggestions(Authentication authentication,
                                                               @PathVariable UUID id,
                                                               @PathVariable UUID photoId,
                                                               @RequestParam(required = false) Double radiusM) {
        return ApiResponse.success(
                photoService.suggestions(principal(authentication), id, photoId, radiusM),
                "Pairing suggestions fetched");
    }

    @Operation(summary = "Pair a damage photo with a documented asset (freezes the asset snapshot)")
    @PostMapping("/api/v1/damage-reports/{id}/pairs")
    public ResponseEntity<ApiResponse<PairItem>> createPair(Authentication authentication,
                                                            @PathVariable UUID id,
                                                            @Valid @RequestBody CreatePairRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        pairService.create(principal(authentication), id, request),
                        "Photos paired"));
    }

    @Operation(summary = "Remove a pair (link only — photos and assets are untouched)")
    @DeleteMapping("/api/v1/damage-reports/{id}/pairs/{pairId}")
    public ApiResponse<DeleteResponse> deletePair(Authentication authentication,
                                                  @PathVariable UUID id,
                                                  @PathVariable UUID pairId) {
        return ApiResponse.success(pairService.delete(principal(authentication), id, pairId),
                "Pair removed");
    }

    @Operation(summary = "Complete the report: freezes everything and computes total loss")
    @PutMapping("/api/v1/damage-reports/{id}/complete")
    public ApiResponse<CompleteResponse> complete(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(reportService.complete(principal(authentication), id),
                "Damage report completed");
    }

    static AuthUser principal(Authentication authentication) {
        return (AuthUser) authentication.getPrincipal();
    }
}
