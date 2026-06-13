package com.assetshield.property.repo;

import com.assetshield.property.domain.Asset;
import com.assetshield.property.domain.AssetCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    /** count + value sum per property — avoids N+1 on property lists. */
    interface PropertyTotals {
        UUID getPropertyId();

        long getAssetCount();

        BigDecimal getTotalValue();
    }

    /** count + value sum per category — the property dashboard. */
    interface CategoryTotals {
        AssetCategory getCategory();

        long getAssetCount();

        BigDecimal getTotalValue();
    }

    Optional<Asset> findByIdAndDeletedAtIsNull(UUID id);

    long countByPropertyIdAndDeletedAtIsNull(UUID propertyId);

    boolean existsByPropertyIdAndSha256HashAndDeletedAtIsNull(UUID propertyId, String sha256Hash);

    Page<Asset> findByPropertyIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID propertyId, Pageable pageable);

    Page<Asset> findByPropertyIdAndCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID propertyId, AssetCategory category, Pageable pageable);

    @Query("""
            select a.propertyId as propertyId, count(a) as assetCount,
                   coalesce(sum(a.estimatedValue), 0) as totalValue
            from Asset a
            where a.propertyId in :propertyIds and a.deletedAt is null
            group by a.propertyId
            """)
    List<PropertyTotals> totalsByProperty(@Param("propertyIds") Collection<UUID> propertyIds);

    @Query("""
            select a.category as category, count(a) as assetCount,
                   coalesce(sum(a.estimatedValue), 0) as totalValue
            from Asset a
            where a.propertyId = :propertyId and a.deletedAt is null
            group by a.category
            order by a.category
            """)
    List<CategoryTotals> totalsByCategory(@Param("propertyId") UUID propertyId);

    /** Bounding-box prefilter on ix_assets_gps; exact Haversine runs in service code. */
    @Query("""
            select a from Asset a
            where a.propertyId = :propertyId and a.deletedAt is null
              and a.gpsLat between :minLat and :maxLat
              and a.gpsLng between :minLng and :maxLng
            """)
    List<Asset> findInBoundingBox(@Param("propertyId") UUID propertyId,
                                  @Param("minLat") BigDecimal minLat, @Param("maxLat") BigDecimal maxLat,
                                  @Param("minLng") BigDecimal minLng, @Param("maxLng") BigDecimal maxLng);

    @Modifying(flushAutomatically = true)
    @Query("update Asset a set a.deletedAt = :now where a.propertyId = :propertyId and a.deletedAt is null")
    int softDeleteByProperty(@Param("propertyId") UUID propertyId, @Param("now") Instant now);
}
