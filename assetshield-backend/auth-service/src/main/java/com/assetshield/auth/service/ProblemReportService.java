package com.assetshield.auth.service;

import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.common.ErrorCode;
import com.assetshield.auth.common.PageEnvelope;
import com.assetshield.auth.domain.ProblemReport;
import com.assetshield.auth.domain.ReportCategory;
import com.assetshield.auth.domain.ReportStatus;
import com.assetshield.auth.domain.User;
import com.assetshield.auth.repo.ProblemReportRepository;
import com.assetshield.auth.repo.UserRepository;
import com.assetshield.auth.web.dto.AuthDtos.ReportItem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User-filed support tickets: create (any user), triage (admins). */
@Service
public class ProblemReportService {

    private final ProblemReportRepository reportRepository;
    private final UserRepository userRepository;

    public ProblemReportService(ProblemReportRepository reportRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public UUID create(UUID reporterUserId, ReportCategory category, String message, String context) {
        ProblemReport report = new ProblemReport();
        report.setReporterUserId(reporterUserId);
        report.setCategory(category);
        report.setMessage(message.trim());
        report.setContext(context == null || context.isBlank() ? null : context.trim());
        return reportRepository.save(report).getId();
    }

    @Transactional(readOnly = true)
    public PageEnvelope<ReportItem> list(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size));
        Page<ProblemReport> reports = parseStatus(status)
                .map(s -> reportRepository.findByStatusOrderByCreatedAtDesc(s, pageable))
                .orElseGet(() -> reportRepository.findAllByOrderByStatusAscCreatedAtDesc(pageable));

        // batch-resolve reporter identity from the local users table (no N+1)
        List<UUID> reporterIds = reports.map(ProblemReport::getReporterUserId).getContent();
        Map<UUID, User> users = reporterIds.isEmpty() ? Map.of()
                : userRepository.findAllById(reporterIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        return PageEnvelope.of(reports.map(r -> {
            User reporter = users.get(r.getReporterUserId());
            return new ReportItem(r.getId(), r.getCategory(), r.getMessage(), r.getContext(),
                    r.getStatus().name(), r.getReporterUserId(),
                    reporter == null ? null : reporter.getFullName(),
                    reporter == null ? null : reporter.getPhoneNumber(),
                    r.getCreatedAt(), r.getResolvedAt());
        }));
    }

    @Transactional
    public void resolve(UUID adminUserId, UUID reportId) {
        ProblemReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found"));
        if (report.getStatus() == ReportStatus.OPEN) { // idempotent
            report.setStatus(ReportStatus.RESOLVED);
            report.setResolvedAt(Instant.now());
            report.setResolvedByUserId(adminUserId);
            reportRepository.save(report);
        }
    }

    private static java.util.Optional<ReportStatus> parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(ReportStatus.valueOf(status.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
