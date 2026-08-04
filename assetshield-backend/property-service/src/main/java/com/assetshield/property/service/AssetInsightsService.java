package com.assetshield.property.service;

import com.assetshield.property.access.AccessLevel;
import com.assetshield.property.access.PropertyAccessService;
import com.assetshield.property.common.ApiException;
import com.assetshield.property.common.ErrorCode;
import com.assetshield.property.domain.Asset;
import com.assetshield.property.domain.AssetReceipt;
import com.assetshield.property.domain.Property;
import com.assetshield.property.repo.AssetReceiptRepository;
import com.assetshield.property.repo.AssetRepository;
import com.assetshield.property.repo.PropertyRepository;
import com.assetshield.property.security.AuthUser;
import com.assetshield.property.web.dto.PropertyDtos.AnalyticsPropertyLine;
import com.assetshield.property.web.dto.PropertyDtos.AssetAnalyticsResponse;
import com.assetshield.property.web.dto.PropertyDtos.CategoryLine;
import com.assetshield.property.web.dto.PropertyDtos.TimelineEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derived read models on top of the evidence tables: CSV export, the
 * cross-property analytics rollup and the per-property history timeline.
 * Nothing here ever mutates evidence.
 */
@Service
public class AssetInsightsService {

    /** More properties than any real account; keeps the rollup query bounded. */
    private static final int MAX_ANALYTICS_PROPERTIES = 200;
    private static final int MAX_TIMELINE_EVENTS = 500;

    private final AssetRepository assetRepository;
    private final AssetReceiptRepository receiptRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyAccessService accessService;

    public AssetInsightsService(AssetRepository assetRepository,
                                AssetReceiptRepository receiptRepository,
                                PropertyRepository propertyRepository,
                                PropertyAccessService accessService) {
        this.assetRepository = assetRepository;
        this.receiptRepository = receiptRepository;
        this.propertyRepository = propertyRepository;
        this.accessService = accessService;
    }

    public record CsvExport(String filename, String csv) {
    }

    /** Owner or a member with the export grant — plain MEMBERs are refused. */
    @Transactional(readOnly = true)
    public CsvExport exportCsv(AuthUser user, UUID propertyId) {
        Property property = accessService.requireProperty(propertyId);
        AccessLevel access = accessService.accessTo(property, user.id());
        if (!access.canView()) {
            throw new ApiException(ErrorCode.NOT_MEMBER, "You are not a member of this property");
        }
        if (access == AccessLevel.MEMBER) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Exporting needs the owner or a member with export permission");
        }
        List<Asset> assets = assetRepository.findByPropertyIdAndDeletedAtIsNullOrderByCreatedAtAsc(propertyId);
        Map<UUID, Long> receiptCounts = assets.isEmpty() ? Map.of()
                : receiptRepository.countsByAsset(assets.stream().map(Asset::getId).toList()).stream()
                        .collect(Collectors.toMap(AssetReceiptRepository.ReceiptCount::getAssetId,
                                AssetReceiptRepository.ReceiptCount::getReceiptCount));

        // BOM so Excel opens the file as UTF-8 instead of the ANSI codepage
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Asset ID,Description,Category,Estimated Value (GHS),Captured At,")
                .append("GPS Lat,GPS Lng,SHA-256,Warranty Expires,Next Service,Receipts,Created At\r\n");
        for (Asset asset : assets) {
            csv.append(String.join(",",
                    field(asset.getId().toString()),
                    field(asset.getDescription()),
                    field(asset.getCategory().name()),
                    field(asset.getEstimatedValue() == null ? "" : asset.getEstimatedValue().toPlainString()),
                    field(asset.getCapturedAt().toString()),
                    field(asset.getGpsLat() == null ? "" : asset.getGpsLat().toPlainString()),
                    field(asset.getGpsLng() == null ? "" : asset.getGpsLng().toPlainString()),
                    field(asset.getSha256Hash()),
                    field(asset.getWarrantyExpiresOn() == null ? "" : asset.getWarrantyExpiresOn().toString()),
                    field(asset.getNextServiceOn() == null ? "" : asset.getNextServiceOn().toString()),
                    field(String.valueOf(receiptCounts.getOrDefault(asset.getId(), 0L))),
                    field(asset.getCreatedAt().toString())))
                    .append("\r\n");
        }
        String safeName = property.getName().replaceAll("[^A-Za-z0-9-]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return new CsvExport("assetshield-" + (safeName.isEmpty() ? "assets" : safeName) + ".csv",
                csv.toString());
    }

    /**
     * RFC-4180 quoting plus a leading apostrophe on =+-@ so a malicious
     * description can never execute as a spreadsheet formula.
     */
    private static String field(String value) {
        String v = value;
        if (!v.isEmpty() && (v.charAt(0) == '=' || v.charAt(0) == '+'
                || v.charAt(0) == '-' || v.charAt(0) == '@')) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** Portfolio rollup over every property the caller owns or belongs to. */
    @Transactional(readOnly = true)
    public AssetAnalyticsResponse analytics(AuthUser user) {
        List<Property> properties = propertyRepository
                .findAccessible(user.id(), PageRequest.of(0, MAX_ANALYTICS_PROPERTIES)).getContent();
        List<UUID> ids = properties.stream().map(Property::getId).toList();
        List<CategoryLine> byCategory = ids.isEmpty() ? List.of()
                : assetRepository.totalsByCategoryForProperties(ids).stream()
                        .map(t -> new CategoryLine(t.getCategory(), t.getAssetCount(), t.getTotalValue()))
                        .toList();
        Map<UUID, AssetRepository.PropertyTotals> totals = ids.isEmpty() ? Map.of()
                : assetRepository.totalsByProperty(ids).stream()
                        .collect(Collectors.toMap(AssetRepository.PropertyTotals::getPropertyId,
                                Function.identity()));
        List<AnalyticsPropertyLine> byProperty = properties.stream()
                .map(p -> {
                    AssetRepository.PropertyTotals t = totals.get(p.getId());
                    return new AnalyticsPropertyLine(p.getId(), p.getName(),
                            t == null ? 0 : t.getAssetCount(),
                            t == null ? BigDecimal.ZERO : t.getTotalValue());
                })
                .sorted(Comparator.comparing(AnalyticsPropertyLine::totalValue).reversed())
                .toList();
        long assetCount = byCategory.stream().mapToLong(CategoryLine::count).sum();
        BigDecimal totalValue = byCategory.stream().map(CategoryLine::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AssetAnalyticsResponse(properties.size(), assetCount, totalValue,
                byCategory, byProperty);
    }

    /**
     * History derived from what the tables already record — creation, upload,
     * receipt and removal timestamps. Soft-deleted rows stay in: the events
     * happened, and members saw those assets while they were live.
     */
    @Transactional(readOnly = true)
    public List<TimelineEvent> timeline(AuthUser user, UUID propertyId) {
        Property property = accessService.requireMember(propertyId, user.id());
        List<TimelineEvent> events = new ArrayList<>();
        events.add(new TimelineEvent("PROPERTY_CREATED", property.getCreatedAt(), null,
                property.getName()));
        List<Asset> assets = assetRepository.findByPropertyIdOrderByCreatedAtAsc(propertyId);
        Map<UUID, String> labels = assets.stream()
                .collect(Collectors.toMap(Asset::getId, Asset::getDescription));
        for (Asset asset : assets) {
            events.add(new TimelineEvent("ASSET_ADDED", asset.getCreatedAt(), asset.getId(),
                    asset.getDescription()));
            if (asset.getDeletedAt() != null) {
                events.add(new TimelineEvent("ASSET_REMOVED", asset.getDeletedAt(), asset.getId(),
                        asset.getDescription()));
            }
        }
        if (!assets.isEmpty()) {
            for (AssetReceipt receipt : receiptRepository
                    .findByAssetIdInOrderByCreatedAtAsc(labels.keySet())) {
                events.add(new TimelineEvent("RECEIPT_ADDED", receipt.getCreatedAt(),
                        receipt.getAssetId(), labels.getOrDefault(receipt.getAssetId(), "Asset")));
            }
        }
        return events.stream()
                .sorted(Comparator.comparing(TimelineEvent::at).reversed())
                .limit(MAX_TIMELINE_EVENTS)
                .toList();
    }
}
