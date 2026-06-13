package com.assetshield.damage.repo;

import com.assetshield.damage.domain.Dossier;
import com.assetshield.damage.domain.DossierStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DossierRepository extends JpaRepository<Dossier, UUID> {

    Optional<Dossier> findByShareToken(UUID shareToken);

    Optional<Dossier> findFirstByDamageReportIdAndStatusInOrderByCreatedAtDesc(
            UUID damageReportId, Collection<DossierStatus> statuses);

    Page<Dossier> findByRequestedByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
