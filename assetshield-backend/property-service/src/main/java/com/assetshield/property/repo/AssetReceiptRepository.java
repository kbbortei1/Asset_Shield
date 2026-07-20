package com.assetshield.property.repo;

import com.assetshield.property.domain.AssetReceipt;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetReceiptRepository extends JpaRepository<AssetReceipt, UUID> {

    interface ReceiptCount {
        UUID getAssetId();

        long getReceiptCount();
    }

    List<AssetReceipt> findByAssetIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID assetId);

    /** Timeline: soft-deleted receipts included — the add event still happened. */
    List<AssetReceipt> findByAssetIdInOrderByCreatedAtAsc(Collection<UUID> assetIds);

    long countByAssetIdAndDeletedAtIsNull(UUID assetId);

    @Query("""
            select r.assetId as assetId, count(r) as receiptCount
            from AssetReceipt r
            where r.assetId in :assetIds and r.deletedAt is null
            group by r.assetId
            """)
    List<ReceiptCount> countsByAsset(@Param("assetIds") Collection<UUID> assetIds);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AssetReceipt r set r.deletedAt = :now
            where r.deletedAt is null
              and r.assetId in (select a.id from Asset a where a.propertyId = :propertyId)
            """)
    int softDeleteByProperty(@Param("propertyId") UUID propertyId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update AssetReceipt r set r.deletedAt = :now where r.assetId = :assetId and r.deletedAt is null")
    int softDeleteByAsset(@Param("assetId") UUID assetId, @Param("now") Instant now);
}
