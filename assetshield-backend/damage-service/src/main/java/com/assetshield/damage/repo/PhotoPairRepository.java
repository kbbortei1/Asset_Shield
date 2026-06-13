package com.assetshield.damage.repo;

import com.assetshield.damage.domain.PhotoPair;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoPairRepository extends JpaRepository<PhotoPair, UUID> {

    List<PhotoPair> findByDamageReportIdOrderByCreatedAtAsc(UUID damageReportId);

    List<PhotoPair> findByDamagePhotoId(UUID damagePhotoId);

    boolean existsByDamagePhotoIdAndAssetId(UUID damagePhotoId, UUID assetId);

    long countByDamageReportId(UUID damageReportId);
}
