package com.assetshield.property.web;

import com.assetshield.property.common.ApiResponse;
import com.assetshield.property.common.PageEnvelope;
import com.assetshield.property.domain.AssetCategory;
import com.assetshield.property.security.AuthUser;
import com.assetshield.property.service.AssetInsightsService;
import com.assetshield.property.service.AssetService;
import com.assetshield.property.service.InvitationService;
import com.assetshield.property.service.PropertyService;
import com.assetshield.property.web.dto.PropertyDtos.AssetResponse;
import com.assetshield.property.web.dto.PropertyDtos.CreateAssetMetadata;
import com.assetshield.property.web.dto.PropertyDtos.CreatePropertyRequest;
import com.assetshield.property.web.dto.PropertyDtos.DeleteResponse;
import com.assetshield.property.web.dto.PropertyDtos.InviteRequest;
import com.assetshield.property.web.dto.PropertyDtos.InviteResponse;
import com.assetshield.property.web.dto.PropertyDtos.MemberItem;
import com.assetshield.property.web.dto.PropertyDtos.OptInRequest;
import com.assetshield.property.web.dto.PropertyDtos.OptInResponse;
import com.assetshield.property.web.dto.PropertyDtos.PropertyDetailResponse;
import com.assetshield.property.web.dto.PropertyDtos.PropertyListItem;
import com.assetshield.property.web.dto.PropertyDtos.PropertyResponse;
import com.assetshield.property.web.dto.PropertyDtos.TimelineEvent;
import com.assetshield.property.web.dto.PropertyDtos.UpdatePropertyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/properties")
@Tag(name = "Properties", description = "Properties, evidence assets and household sharing")
public class PropertyController {

    private final PropertyService propertyService;
    private final AssetService assetService;
    private final AssetInsightsService insightsService;
    private final InvitationService invitationService;
    private final MetadataParser metadataParser;

    public PropertyController(PropertyService propertyService, AssetService assetService,
                              AssetInsightsService insightsService,
                              InvitationService invitationService, MetadataParser metadataParser) {
        this.propertyService = propertyService;
        this.assetService = assetService;
        this.insightsService = insightsService;
        this.invitationService = invitationService;
        this.metadataParser = metadataParser;
    }

    @Operation(summary = "Create a property (FREE tier: max 1)")
    @PostMapping
    public ResponseEntity<ApiResponse<PropertyResponse>> create(Authentication authentication,
                                                                @Valid @RequestBody CreatePropertyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(propertyService.create(principal(authentication), request),
                        "Property created"));
    }

    @Operation(summary = "List owned + member properties with asset totals")
    @GetMapping
    public ApiResponse<PageEnvelope<PropertyListItem>> list(Authentication authentication,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(propertyService.list(principal(authentication), page, size),
                "Properties fetched");
    }

    @Operation(summary = "Property details + per-category dashboard")
    @GetMapping("/{id}")
    public ApiResponse<PropertyDetailResponse> detail(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(propertyService.detail(principal(authentication), id),
                "Property fetched");
    }

    @Operation(summary = "Update property fields (owner only)")
    @PutMapping("/{id}")
    public ApiResponse<PropertyDetailResponse> update(Authentication authentication, @PathVariable UUID id,
                                                      @Valid @RequestBody UpdatePropertyRequest request) {
        return ApiResponse.success(propertyService.update(principal(authentication), id, request),
                "Property updated");
    }

    @Operation(summary = "Soft delete the property, its assets and receipts (owner only)")
    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResponse> delete(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success(propertyService.delete(principal(authentication), id),
                "Property deleted");
    }

    @Operation(summary = "Toggle the marketplace opt-in flag (owner only)")
    @PutMapping("/{id}/offers-optin")
    public ApiResponse<OptInResponse> optIn(Authentication authentication, @PathVariable UUID id,
                                            @Valid @RequestBody OptInRequest request) {
        return ApiResponse.success(propertyService.setOptIn(principal(authentication), id, request),
                "Opt-in updated");
    }

    @Operation(summary = "Create an asset from 1..15 photos (multipart: repeated file parts + metadata JSON)")
    @PostMapping(value = "/{id}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssetResponse>> addAsset(Authentication authentication,
                                                               @PathVariable UUID id,
                                                               @RequestParam("file") List<MultipartFile> files,
                                                               @RequestPart("metadata") String metadata) {
        CreateAssetMetadata parsed = metadataParser.parse(metadata, CreateAssetMetadata.class);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        assetService.addAssets(principal(authentication), id, files, parsed),
                        "Asset captured"));
    }

    @Operation(summary = "List/search assets (?category= &q= &minValue= &maxValue=)")
    @GetMapping("/{id}/assets")
    public ApiResponse<PageEnvelope<AssetResponse>> listAssets(Authentication authentication,
                                                               @PathVariable UUID id,
                                                               @RequestParam(required = false) AssetCategory category,
                                                               @RequestParam(required = false) String q,
                                                               @RequestParam(required = false) BigDecimal minValue,
                                                               @RequestParam(required = false) BigDecimal maxValue,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                assetService.listAssets(principal(authentication), id, category, q,
                        minValue, maxValue, page, size),
                "Assets fetched");
    }

    @Operation(summary = "Export live assets as CSV (owner or export-enabled member)")
    @GetMapping("/{id}/assets/export")
    public ResponseEntity<byte[]> exportAssets(Authentication authentication, @PathVariable UUID id) {
        AssetInsightsService.CsvExport export =
                insightsService.exportCsv(principal(authentication), id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.filename() + "\"")
                .body(export.csv().getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "Derived history timeline (newest first, max 500 events)")
    @GetMapping("/{id}/timeline")
    public ApiResponse<Map<String, List<TimelineEvent>>> timeline(Authentication authentication,
                                                                  @PathVariable UUID id) {
        return ApiResponse.success(
                Map.of("items", insightsService.timeline(principal(authentication), id)),
                "Timeline fetched");
    }

    @Operation(summary = "Invite a phone number to the household (owner only)")
    @PostMapping("/{id}/invite")
    public ResponseEntity<ApiResponse<InviteResponse>> invite(Authentication authentication,
                                                              @PathVariable UUID id,
                                                              @Valid @RequestBody InviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        invitationService.invite(principal(authentication), id, request),
                        "Invitation sent"));
    }

    @Operation(summary = "List active household members (owner only)")
    @GetMapping("/{id}/members")
    public ApiResponse<Map<String, List<MemberItem>>> members(Authentication authentication,
                                                              @PathVariable UUID id) {
        return ApiResponse.success(
                Map.of("items", invitationService.members(principal(authentication), id)),
                "Members fetched");
    }

    @Operation(summary = "Revoke a household membership (owner only)")
    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Map<String, Boolean>> removeMember(Authentication authentication,
                                                          @PathVariable UUID id,
                                                          @PathVariable UUID userId) {
        return ApiResponse.success(
                invitationService.removeMember(principal(authentication), id, userId),
                "Member removed");
    }

    static AuthUser principal(Authentication authentication) {
        return (AuthUser) authentication.getPrincipal();
    }
}
