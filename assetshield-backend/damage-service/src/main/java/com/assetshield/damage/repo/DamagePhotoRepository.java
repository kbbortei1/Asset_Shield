package com.assetshield.damage.repo;

import com.assetshield.damage.domain.DamagePhoto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DamagePhotoRepository extends JpaRepository<DamagePhoto, UUID> {

    Optional<DamagePhoto> findByIdAndDeletedAtIsNull(UUID id);

    List<DamagePhoto> findByDamageReportIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID damageReportId);

    long countByDamageReportIdAndDeletedAtIsNull(UUID damageReportId);

    boolean existsByDamageReportIdAndSha256HashAndDeletedAtIsNull(UUID damageReportId, String sha256Hash);
}
