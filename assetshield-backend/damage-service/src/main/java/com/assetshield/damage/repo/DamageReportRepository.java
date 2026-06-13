package com.assetshield.damage.repo;

import com.assetshield.damage.domain.DamageReport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DamageReportRepository extends JpaRepository<DamageReport, UUID> {

    /** photo + pair counts per report — avoids N+1 on list endpoints. */
    interface ReportCounts {
        UUID getReportId();

        long getPhotoCount();

        long getPairCount();
    }

    Optional<DamageReport> findByIdAndDeletedAtIsNull(UUID id);

    Page<DamageReport> findByPropertyIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID propertyId, Pageable pageable);

    Page<DamageReport> findByCreatedByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("""
            select r.id as reportId,
                   (select count(p) from DamagePhoto p
                    where p.damageReportId = r.id and p.deletedAt is null) as photoCount,
                   (select count(pp) from PhotoPair pp
                    where pp.damageReportId = r.id) as pairCount
            from DamageReport r
            where r.id in :reportIds
            """)
    List<ReportCounts> countsFor(@Param("reportIds") Collection<UUID> reportIds);
}
