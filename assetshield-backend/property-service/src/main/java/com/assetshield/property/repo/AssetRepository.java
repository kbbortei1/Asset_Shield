package com.assetshield.property.repo;

import com.assetshield.property.domain.Asset;
import com.assetshield.property.domain.AssetCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    /** Fraud signal: the same photo bytes documented anywhere in the system. */
    long countBySha256HashAndDeletedAtIsNull(String sha256Hash);

    Page<Asset> findByPropertyIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID propertyId, Pageable pageable);

    Page<Asset> findByPropertyIdAndCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID propertyId, AssetCategory category, Pageable pageable);

    /** CSV export: everything live on the property, oldest first. */
    List<Asset> findByPropertyIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID propertyId);

    /** Timeline: soft-deleted rows included — their add/remove events still happened. */
    List<Asset> findByPropertyIdOrderByCreatedAtAsc(UUID propertyId);

    // Search follows the leads idiom: callers pass q = "" for "no text filter"
    // and explicit min/max bounds instead of nullable parameters (PostgreSQL
    // types a null bind inside lower(?)/between as bytea and blows up).

    @Query("""
            select a from Asset a
            where a.propertyId = :propertyId and a.deletedAt is null
              and lower(a.description) like lower(concat('%', :q, '%'))
              and a.estimatedValue between :minValue and :maxValue
            order by a.createdAt desc
            """)
    Page<Asset> search(@Param("propertyId") UUID propertyId, @Param("q") String q,
                       @Param("minValue") BigDecimal minValue, @Param("maxValue") BigDecimal maxValue,
                       Pageable pageable);

    @Query("""
            select a from Asset a
            where a.propertyId = :propertyId and a.deletedAt is null
              and a.category = :category
              and lower(a.description) like lower(concat('%', :q, '%'))
              and a.estimatedValue between :minValue and :maxValue
            order by a.createdAt desc
            """)
    Page<Asset> searchByCategory(@Param("propertyId") UUID propertyId,
                                 @Param("category") AssetCategory category, @Param("q") String q,
                                 @Param("minValue") BigDecimal minValue,
                                 @Param("maxValue") BigDecimal maxValue, Pageable pageable);

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

    /** Analytics rollup across every property the caller can access. */
    @Query("""
            select a.category as category, count(a) as assetCount,
                   coalesce(sum(a.estimatedValue), 0) as totalValue
            from Asset a
            where a.propertyId in :propertyIds and a.deletedAt is null
            group by a.category
            order by a.category
            """)
    List<CategoryTotals> totalsByCategoryForProperties(@Param("propertyIds") Collection<UUID> propertyIds);

    /** Maintenance sweep feed row (asset joined to its live property). */
    interface MaintenanceDueRow {
        UUID getAssetId();

        UUID getPropertyId();

        String getPropertyName();

        UUID getOwnerUserId();

        String getDescription();

        LocalDate getDueOn();
    }

    @Query("""
            select a.id as assetId, a.propertyId as propertyId, p.name as propertyName,
                   p.ownerUserId as ownerUserId, a.description as description,
                   a.warrantyExpiresOn as dueOn
            from Asset a, Property p
            where a.propertyId = p.id and a.deletedAt is null and p.deletedAt is null
              and a.warrantyExpiresOn between :from and :to
            order by a.warrantyExpiresOn asc, a.id asc
            """)
    Page<MaintenanceDueRow> warrantyDueBetween(@Param("from") LocalDate from,
                                               @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            select a.id as assetId, a.propertyId as propertyId, p.name as propertyName,
                   p.ownerUserId as ownerUserId, a.description as description,
                   a.nextServiceOn as dueOn
            from Asset a, Property p
            where a.propertyId = p.id and a.deletedAt is null and p.deletedAt is null
              and a.nextServiceOn between :from and :to
            order by a.nextServiceOn asc, a.id asc
            """)
    Page<MaintenanceDueRow> serviceDueBetween(@Param("from") LocalDate from,
                                              @Param("to") LocalDate to, Pageable pageable);
}
