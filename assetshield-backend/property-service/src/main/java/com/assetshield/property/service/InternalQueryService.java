package com.assetshield.property.service;

import com.assetshield.property.access.PropertyAccessService;
import com.assetshield.property.client.AuthUserClient;
import com.assetshield.property.common.ApiException;
import com.assetshield.property.common.ErrorCode;
import com.assetshield.property.common.PageEnvelope;
import com.assetshield.property.config.AppProperties;
import com.assetshield.property.domain.Asset;
import com.assetshield.property.domain.Property;
import com.assetshield.property.domain.PropertyType;
import com.assetshield.property.repo.AssetRepository;
import com.assetshield.property.repo.PropertyRepository;
import com.assetshield.property.storage.StorageProvider;
import com.assetshield.property.web.dto.PropertyDtos.AccessResponse;
import com.assetshield.property.web.dto.PropertyDtos.AssetNearItem;
import com.assetshield.property.web.dto.PropertyDtos.InternalAssetResponse;
import com.assetshield.property.web.dto.PropertyDtos.InternalPropertyResponse;
import com.assetshield.property.web.dto.PropertyDtos.CategoryLine;
import com.assetshield.property.web.dto.PropertyDtos.LeadListItem;
import com.assetshield.property.web.dto.PropertyDtos.LeadViewResponse;
import com.assetshield.property.web.dto.PropertyDtos.MaintenanceDueItem;
import com.assetshield.property.web.dto.PropertyDtos.StaleDocumentationItem;
import com.assetshield.property.web.dto.PropertyDtos.TipsContextResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read models served on /internal/** to the other AssetShield services. */
@Service
public class InternalQueryService {

    private final PropertyRepository propertyRepository;
    private final AssetRepository assetRepository;
    private final PropertyAccessService accessService;
    private final AuthUserClient authUserClient;
    private final StorageProvider storageProvider;
    private final Duration signedUrlTtl;

    public InternalQueryService(PropertyRepository propertyRepository, AssetRepository assetRepository,
                                PropertyAccessService accessService, AuthUserClient authUserClient,
                                StorageProvider storageProvider, AppProperties properties) {
        this.propertyRepository = propertyRepository;
        this.assetRepository = assetRepository;
        this.accessService = accessService;
        this.authUserClient = authUserClient;
        this.storageProvider = storageProvider;
        this.signedUrlTtl = Duration.ofMinutes(properties.storage().signedUrlTtlMinutes());
    }

    /** Soft-deleted properties are still returned, flagged deleted=true. */
    @Transactional(readOnly = true)
    public InternalPropertyResponse property(UUID id) {
        Property p = propertyRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Property not found"));
        return new InternalPropertyResponse(p.getId(), p.getOwnerUserId(), p.getName(), p.getType(),
                p.getLocality(), p.isOpenToOffers(), p.getDeletedAt() != null);
    }

    @Transactional(readOnly = true)
    public AccessResponse access(UUID propertyId, UUID userId) {
        return new AccessResponse(accessService.access(propertyId, userId).name());
    }

    @Transactional(readOnly = true)
    public InternalAssetResponse asset(UUID id) {
        Asset a = assetRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Asset not found"));
        return new InternalAssetResponse(a.getId(), a.getPropertyId(), a.getPhotoUrl(), a.getSha256Hash(),
                a.getGpsLat(), a.getGpsLng(), a.getCapturedAt(), a.getDescription(),
                a.getEstimatedValue(), a.getCategory(), a.getCreatedByUserId(), a.getCreatedAt());
    }

    /**
     * Bounding-box prefilter on the GPS index, exact Haversine afterwards,
     * ascending by distance. Backs damage-service before/after pairing.
     */
    @Transactional(readOnly = true)
    public List<AssetNearItem> assetsNear(UUID propertyId, double lat, double lng, double radiusM) {
        accessService.requireProperty(propertyId);
        GeoMath.BoundingBox box = GeoMath.boundingBox(lat, lng, radiusM);
        return assetRepository.findInBoundingBox(propertyId,
                        BigDecimal.valueOf(box.minLat()), BigDecimal.valueOf(box.maxLat()),
                        BigDecimal.valueOf(box.minLng()), BigDecimal.valueOf(box.maxLng())).stream()
                .map(a -> new AssetNearItem(a.getId(),
                        GeoMath.haversineMeters(lat, lng,
                                a.getGpsLat().doubleValue(), a.getGpsLng().doubleValue()),
                        a.getDescription(), a.getEstimatedValue(), a.getCategory(),
                        storageProvider.signedUrl(a.getPhotoUrl(), signedUrlTtl),
                        a.getSha256Hash(), a.getCapturedAt()))
                .filter(item -> item.distanceMeters() <= radiusM)
                .sorted(Comparator.comparingDouble(AssetNearItem::distanceMeters))
                .toList();
    }

    /**
     * Marketplace leads list: opted-in, live properties only, locality as a
     * case-insensitive contains. Items carry the same reduced owner display
     * name as the single lead view — never more.
     */
    @Transactional(readOnly = true)
    public PageEnvelope<LeadListItem> leads(String propertyType, String locality, int page, int size) {
        PropertyType type = parseType(propertyType);
        if (propertyType != null && !propertyType.isBlank() && type == null) {
            // unknown type filter matches nothing rather than erroring
            return new PageEnvelope<>(List.of(), PageEnvelope.clampPage(page),
                    PageEnvelope.clampSize(size), 0, 0);
        }
        String localityFilter = locality == null || locality.isBlank() ? "" : locality.trim();
        Pageable pageable = PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size));
        var leads = type == null
                ? propertyRepository.findLeads(localityFilter, pageable)
                : propertyRepository.findLeadsByType(type, localityFilter, pageable);
        return PageEnvelope.of(leads
                .map(p -> new LeadListItem(p.getId(),
                        authUserClient.byId(p.getOwnerUserId())
                                .map(info -> displayName(info.fullName()))
                                .orElse("Owner"),
                        p.getName(), p.getType(), p.getLocality())));
    }

    private static PropertyType parseType(String propertyType) {
        if (propertyType == null || propertyType.isBlank()) {
            return null;
        }
        try {
            return PropertyType.valueOf(propertyType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The ONLY projection the marketplace may consume: exactly six fields,
     * owner reduced to "first name + last initial".
     */
    @Transactional(readOnly = true)
    public LeadViewResponse leadView(UUID propertyId) {
        Property p = accessService.requireProperty(propertyId);
        String displayName = authUserClient.byId(p.getOwnerUserId())
                .map(info -> displayName(info.fullName()))
                .orElse("Owner");
        return new LeadViewResponse(p.getId(), displayName, p.getName(), p.getType(),
                p.getLocality(), p.isOpenToOffers());
    }

    /** Rule-engine input for Day 6's tips engine (reuses the dashboard GROUP BY). */
    @Transactional(readOnly = true)
    public TipsContextResponse tipsContext(UUID propertyId) {
        Property p = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Property not found"));
        List<CategoryLine> byCategory = assetRepository.totalsByCategory(propertyId).stream()
                .map(t -> new CategoryLine(t.getCategory(), t.getAssetCount(), t.getTotalValue()))
                .toList();
        return new TipsContextResponse(p.getId(), p.getOwnerUserId(), p.getType(),
                p.getGpsLat(), p.getGpsLng(), byCategory);
    }

    /**
     * Maintenance sweep feed: live assets whose warranty ({@code kind=WARRANTY})
     * or service date ({@code kind=SERVICE}) falls within the next
     * {@code withinDays} days. Africa/Accra dates — the sweep runs on that clock.
     */
    @Transactional(readOnly = true)
    public PageEnvelope<MaintenanceDueItem> maintenanceDue(String kind, int withinDays,
                                                           int page, int size) {
        String normalized = kind == null ? "" : kind.trim().toUpperCase();
        if (!normalized.equals("WARRANTY") && !normalized.equals("SERVICE")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "kind must be WARRANTY or SERVICE");
        }
        LocalDate from = LocalDate.now(ZoneId.of("Africa/Accra"));
        LocalDate to = from.plusDays(Math.clamp(withinDays, 1, 60));
        Pageable pageable = PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size));
        Page<AssetRepository.MaintenanceDueRow> rows = normalized.equals("WARRANTY")
                ? assetRepository.warrantyDueBetween(from, to, pageable)
                : assetRepository.serviceDueBetween(from, to, pageable);
        return PageEnvelope.of(rows.map(row -> new MaintenanceDueItem(row.getAssetId(),
                row.getPropertyId(), row.getPropertyName(), row.getOwnerUserId(),
                row.getDescription(), normalized, row.getDueOn())));
    }

    /** Redoc sweep feed: documented once, stale for more than {@code days}. */
    @Transactional(readOnly = true)
    public PageEnvelope<StaleDocumentationItem> staleDocumentation(int days, int page, int size) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(Math.max(1, days)));
        Pageable pageable = PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size));
        return PageEnvelope.of(propertyRepository.findStaleDocumentation(cutoff, pageable)
                .map(p -> new StaleDocumentationItem(p.getId(), p.getOwnerUserId(), p.getName(),
                        p.getLastDocumentedAt())));
    }

    static String displayName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
