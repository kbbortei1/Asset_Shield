package com.assetshield.property.repo;

import com.assetshield.property.domain.AssetPhoto;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetPhotoRepository extends JpaRepository<AssetPhoto, UUID> {

    /** Photos of one asset, cover first. */
    List<AssetPhoto> findByAssetIdAndDeletedAtIsNullOrderByPositionAsc(UUID assetId);

    /** Tier quota is measured in PHOTOS, not assets. */
    long countByPropertyIdAndDeletedAtIsNull(UUID propertyId);

    /** Per-property duplicate: these exact bytes already document this property. */
    boolean existsByPropertyIdAndSha256HashAndDeletedAtIsNull(UUID propertyId, String sha256Hash);

    /** Fraud signal: the same bytes documented anywhere in the system. */
    long countBySha256HashAndDeletedAtIsNull(String sha256Hash);

    /** photoCount per asset — avoids N+1 on the asset list. */
    interface PhotoCount {
        UUID getAssetId();

        long getPhotoCount();
    }

    @Query("""
            select p.assetId as assetId, count(p) as photoCount
            from AssetPhoto p
            where p.assetId in :assetIds and p.deletedAt is null
            group by p.assetId
            """)
    List<PhotoCount> countsByAsset(@Param("assetIds") Collection<UUID> assetIds);

    @Modifying(flushAutomatically = true)
    @Query("update AssetPhoto p set p.deletedAt = :now where p.assetId = :assetId and p.deletedAt is null")
    int softDeleteByAsset(@Param("assetId") UUID assetId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update AssetPhoto p set p.deletedAt = :now where p.propertyId = :propertyId and p.deletedAt is null")
    int softDeleteByProperty(@Param("propertyId") UUID propertyId, @Param("now") Instant now);
}
