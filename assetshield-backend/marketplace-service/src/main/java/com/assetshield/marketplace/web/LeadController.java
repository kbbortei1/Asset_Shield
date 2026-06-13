package com.assetshield.marketplace.web;

import com.assetshield.marketplace.common.ApiResponse;
import com.assetshield.marketplace.lead.LeadService;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.ExpressInterestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads", description = "Express interest in an opt-in property")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @Operation(summary = "Express interest in a lead (404 unless opted in)")
    @PostMapping("/{propertyId}/express-interest")
    public ResponseEntity<ApiResponse<ExpressInterestResponse>> expressInterest(
            Authentication authentication, @PathVariable UUID propertyId) {
        AuthUser user = (AuthUser) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(leadService.expressInterest(user, propertyId),
                        "Interest recorded"));
    }
}
