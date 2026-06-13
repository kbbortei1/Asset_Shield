package com.assetshield.property.web;

import com.assetshield.property.common.ApiResponse;
import com.assetshield.property.common.PageEnvelope;
import com.assetshield.property.service.InternalQueryService;
import com.assetshield.property.web.dto.PropertyDtos.AccessResponse;
import com.assetshield.property.web.dto.PropertyDtos.AssetNearItem;
import com.assetshield.property.web.dto.PropertyDtos.InternalAssetResponse;
import com.assetshield.property.web.dto.PropertyDtos.InternalPropertyResponse;
import com.assetshield.property.web.dto.PropertyDtos.LeadListItem;
import com.assetshield.property.web.dto.PropertyDtos.LeadViewResponse;
import com.assetshield.property.web.dto.PropertyDtos.StaleDocumentationItem;
import com.assetshield.property.web.dto.PropertyDtos.TipsContextResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service lookups. Never routed by the gateway; reachable only on
 * the internal Docker network and guarded by the X-Internal-Api-Key header.
 */
@RestController
@RequestMapping("/internal")
public class InternalPropertyController {

    private final InternalQueryService internalQueryService;

    public InternalPropertyController(InternalQueryService internalQueryService) {
        this.internalQueryService = internalQueryService;
    }

    @GetMapping("/properties/{id}")
    public ApiResponse<InternalPropertyResponse> property(@PathVariable UUID id) {
        return ApiResponse.success(internalQueryService.property(id), "Property fetched");
    }

    @GetMapping("/properties/{id}/access/{userId}")
    public ApiResponse<AccessResponse> access(@PathVariable UUID id, @PathVariable UUID userId) {
        return ApiResponse.success(internalQueryService.access(id, userId), "Access resolved");
    }

    @GetMapping("/assets/{id}")
    public ApiResponse<InternalAssetResponse> asset(@PathVariable UUID id) {
        return ApiResponse.success(internalQueryService.asset(id), "Asset fetched");
    }

    @GetMapping("/properties/{id}/assets-near")
    public ApiResponse<Map<String, List<AssetNearItem>>> assetsNear(@PathVariable UUID id,
                                                                    @RequestParam double lat,
                                                                    @RequestParam double lng,
                                                                    @RequestParam(defaultValue = "25") double radiusM) {
        return ApiResponse.success(
                Map.of("items", internalQueryService.assetsNear(id, lat, lng, radiusM)),
                "Assets fetched");
    }

    @GetMapping("/properties/{id}/lead-view")
    public ApiResponse<LeadViewResponse> leadView(@PathVariable UUID id) {
        return ApiResponse.success(internalQueryService.leadView(id), "Lead view fetched");
    }

    /** Marketplace leads list — literal path, matched before the {id} routes. */
    @GetMapping("/properties/leads")
    public ApiResponse<PageEnvelope<LeadListItem>> leads(
            @RequestParam(required = false) String propertyType,
            @RequestParam(required = false) String locality,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(internalQueryService.leads(propertyType, locality, page, size),
                "Leads fetched");
    }

    /** Day 6: rule-engine input for notification-service's tips engine. */
    @GetMapping("/properties/{id}/tips-context")
    public ApiResponse<TipsContextResponse> tipsContext(@PathVariable UUID id) {
        return ApiResponse.success(internalQueryService.tipsContext(id), "Tips context fetched");
    }

    /** Day 6: redoc-reminder sweep feed. */
    @GetMapping("/properties/stale-documentation")
    public ApiResponse<PageEnvelope<StaleDocumentationItem>> staleDocumentation(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(internalQueryService.staleDocumentation(days, page, size),
                "Stale documentation fetched");
    }
}
