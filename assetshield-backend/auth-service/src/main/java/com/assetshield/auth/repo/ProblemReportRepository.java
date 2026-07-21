package com.assetshield.auth.repo;

import com.assetshield.auth.domain.ProblemReport;
import com.assetshield.auth.domain.ReportStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemReportRepository extends JpaRepository<ProblemReport, UUID> {

    Page<ProblemReport> findAllByOrderByStatusAscCreatedAtDesc(Pageable pageable);

    Page<ProblemReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    long countByStatus(ReportStatus status);
}
