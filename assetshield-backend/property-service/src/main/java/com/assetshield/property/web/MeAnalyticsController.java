package com.assetshield.property.web;

import com.assetshield.property.common.ApiResponse;
import com.assetshield.property.service.AssetInsightsService;
import com.assetshield.property.web.dto.PropertyDtos.AssetAnalyticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Analytics", description = "Portfolio rollups for the signed-in user")
public class MeAnalyticsController {

    private final AssetInsightsService insightsService;

    public MeAnalyticsController(AssetInsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @Operation(summary = "Totals + per-category and per-property breakdown across all my properties")
    @GetMapping("/asset-analytics")
    public ApiResponse<AssetAnalyticsResponse> analytics(Authentication authentication) {
        return ApiResponse.success(
                insightsService.analytics(PropertyController.principal(authentication)),
                "Analytics fetched");
    }
}
