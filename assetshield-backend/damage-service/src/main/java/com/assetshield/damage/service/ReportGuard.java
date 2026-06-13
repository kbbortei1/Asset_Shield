package com.assetshield.damage.service;

import com.assetshield.damage.client.AccessLevel;
import com.assetshield.damage.client.PropertyInternalClient;
import com.assetshield.damage.common.ApiException;
import com.assetshield.damage.common.ErrorCode;
import com.assetshield.damage.domain.DamageReport;
import com.assetshield.damage.domain.ReportStatus;
import com.assetshield.damage.repo.DamageReportRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Central authorization + state-machine guard used by every endpoint.
 *
 * <pre>
 * Action                                       OWNER  MEMBER_EXPORT  MEMBER
 * create report / photos / pairs / complete      ✅        ✅          ❌
 * view reports, photos, pairs                    ✅        ✅          ✅
 * </pre>
 *
 * Every mutation additionally requires status = DRAFT — a COMPLETED report
 * and everything under it is immutable evidence.
 */
@Service
public class ReportGuard {

    private final PropertyInternalClient propertyClient;
    private final DamageReportRepository reportRepository;

    public ReportGuard(PropertyInternalClient propertyClient, DamageReportRepository reportRepository) {
        this.propertyClient = propertyClient;
        this.reportRepository = reportRepository;
    }

    public AccessLevel requireView(UUID propertyId, UUID userId) {
        AccessLevel access = propertyClient.access(propertyId, userId);
        if (!access.canView()) {
            throw new ApiException(ErrorCode.NOT_MEMBER, "You are not a member of this property");
        }
        return access;
    }

    public AccessLevel requireMutate(UUID propertyId, UUID userId) {
        AccessLevel access = propertyClient.access(propertyId, userId);
        if (access == AccessLevel.NONE) {
            throw new ApiException(ErrorCode.NOT_MEMBER, "You are not a member of this property");
        }
        if (!access.canMutate()) {
            throw new ApiException(ErrorCode.NOT_OWNER,
                    "Only the owner or an export-rights member can modify damage reports");
        }
        return access;
    }

    public DamageReport requireReport(UUID reportId) {
        return reportRepository.findByIdAndDeletedAtIsNull(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Damage report not found"));
    }

    public void requireDraft(DamageReport report) {
        if (report.getStatus() != ReportStatus.DRAFT) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This damage report is completed and can no longer be modified");
        }
    }
}
