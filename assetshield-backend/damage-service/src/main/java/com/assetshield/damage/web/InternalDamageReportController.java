package com.assetshield.damage.web;

import com.assetshield.damage.common.ApiResponse;
import com.assetshield.damage.service.DamageReportService;
import com.assetshield.damage.web.dto.DamageDtos.InternalReportResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service lookups (Day 4 dossier PDF builder, Day 5 marketplace).
 * Never routed by the gateway; reachable only on the internal Docker network
 * and guarded by the X-Internal-Api-Key header.
 */
@RestController
@RequestMapping("/internal/damage-reports")
public class InternalDamageReportController {

    private final DamageReportService reportService;

    public InternalDamageReportController(DamageReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{id}")
    public ApiResponse<InternalReportResponse> report(@PathVariable UUID id) {
        return ApiResponse.success(reportService.internalReport(id), "Damage report fetched");
    }
}
