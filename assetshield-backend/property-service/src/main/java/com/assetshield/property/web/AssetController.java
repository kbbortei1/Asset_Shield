package com.assetshield.property.web;

import com.assetshield.property.common.ApiResponse;
import com.assetshield.property.security.AuthUser;
import com.assetshield.property.service.AssetService;
import com.assetshield.property.web.dto.PropertyDtos.AssetDetailResponse;
import com.assetshield.property.web.dto.PropertyDtos.DeleteResponse;
import com.assetshield.property.web.dto.PropertyDtos.ReceiptMetadata;
import com.assetshield.property.web.dto.PropertyDtos.ReceiptResponse;
import com.assetshield.property.web.dto.PropertyDtos.UpdateAssetRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/assets")
@Tag(name = "Assets", description = "Single-asset reads, evidence-safe edits and receipts")
public class AssetController {

    private final AssetService assetService;
    private final MetadataParser metadataParser;

    public AssetController(AssetService assetService, MetadataParser metadataParser) {
        this.assetService = assetService;
        this.metadataParser = metadataParser;
    }

    @Operation(summary = "Asset details + receipts (access via parent property)")
    @GetMapping("/{id}")
    public ApiResponse<AssetDetailResponse> get(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(
                assetService.getAsset(PropertyController.principal(authentication), id),
                "Asset fetched");
    }

    @Operation(summary = "Edit description/value/category (photo, hash, GPS, capturedAt immutable)")
    @PutMapping("/{id}")
    public ApiResponse<AssetDetailResponse> update(Authentication authentication, @PathVariable UUID id,
                                                   @Valid @RequestBody UpdateAssetRequest request) {
        return ApiResponse.success(
                assetService.updateAsset(PropertyController.principal(authentication), id, request),
                "Asset updated");
    }

    @Operation(summary = "Soft delete an asset (owner, or the member who uploaded it)")
    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResponse> delete(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(
                assetService.deleteAsset(PropertyController.principal(authentication), id),
                "Asset deleted");
    }

    @Operation(summary = "Attach a receipt photo (multipart: file + metadata JSON)")
    @PostMapping(value = "/{id}/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ReceiptResponse>> addReceipt(Authentication authentication,
                                                                   @PathVariable UUID id,
                                                                   @RequestPart("file") MultipartFile file,
                                                                   @RequestPart("metadata") String metadata) {
        ReceiptMetadata parsed = metadataParser.parse(metadata, ReceiptMetadata.class);
        AuthUser user = PropertyController.principal(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(assetService.addReceipt(user, id, file, parsed),
                        "Receipt attached"));
    }
}
