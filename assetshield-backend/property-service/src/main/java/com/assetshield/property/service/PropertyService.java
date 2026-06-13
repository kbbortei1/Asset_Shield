package com.assetshield.property.service;

import com.assetshield.property.access.AccessLevel;
import com.assetshield.property.access.PropertyAccessService;
import com.assetshield.property.client.MarketplaceEventsClient;
import com.assetshield.property.common.PageEnvelope;
import com.assetshield.property.domain.Property;
import com.assetshield.property.repo.AssetReceiptRepository;
import com.assetshield.property.repo.AssetRepository;
import com.assetshield.property.repo.PropertyRepository;
import com.assetshield.property.security.AuthUser;
import com.assetshield.property.web.dto.PropertyDtos.CategoryLine;
import com.assetshield.property.web.dto.PropertyDtos.CreatePropertyRequest;
import com.assetshield.property.web.dto.PropertyDtos.Dashboard;
import com.assetshield.property.web.dto.PropertyDtos.DeleteResponse;
import com.assetshield.property.web.dto.PropertyDtos.OptInRequest;
import com.assetshield.property.web.dto.PropertyDtos.OptInResponse;
import com.assetshield.property.web.dto.PropertyDtos.PropertyDetailResponse;
import com.assetshield.property.web.dto.PropertyDtos.PropertyListItem;
import com.assetshield.property.web.dto.PropertyDtos.PropertyResponse;
import com.assetshield.property.web.dto.PropertyDtos.UpdatePropertyRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final AssetRepository assetRepository;
    private final AssetReceiptRepository receiptRepository;
    private final PropertyAccessService accessService;
    private final FreeTierGuard freeTierGuard;
    private final MarketplaceEventsClient marketplaceEvents;

    public PropertyService(PropertyRepository propertyRepository, AssetRepository assetRepository,
                           AssetReceiptRepository receiptRepository, PropertyAccessService accessService,
                           FreeTierGuard freeTierGuard, MarketplaceEventsClient marketplaceEvents) {
        this.propertyRepository = propertyRepository;
        this.assetRepository = assetRepository;
        this.receiptRepository = receiptRepository;
        this.accessService = accessService;
        this.freeTierGuard = freeTierGuard;
        this.marketplaceEvents = marketplaceEvents;
    }

    @Transactional
    public PropertyResponse create(AuthUser user, CreatePropertyRequest request) {
        freeTierGuard.checkPropertyQuota(user.id(),
                propertyRepository.countByOwnerUserIdAndDeletedAtIsNull(user.id()));
        Property property = new Property();
        property.setOwnerUserId(user.id());
        property.setName(request.name().trim());
        property.setType(request.type());
        property.setGpsLat(request.gpsLat());
        property.setGpsLng(request.gpsLng());
        property.setLocality(request.locality().trim());
        // flush so @CreationTimestamp is populated before the DTO is built
        Property saved = propertyRepository.saveAndFlush(property);
        return new PropertyResponse(saved.getId(), saved.getName(), saved.getType(), saved.getLocality(),
                saved.getGpsLat(), saved.getGpsLng(), saved.isOpenToOffers(), saved.getOpenToOffersAt(),
                saved.getLastDocumentedAt(), 0, BigDecimal.ZERO, AccessLevel.OWNER.name(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PageEnvelope<PropertyListItem> list(AuthUser user, int page, int size) {
        Page<Property> properties = propertyRepository.findAccessible(user.id(),
                PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)));
        List<UUID> ids = properties.map(Property::getId).getContent();
        Map<UUID, AssetRepository.PropertyTotals> totals = ids.isEmpty() ? Map.of()
                : assetRepository.totalsByProperty(ids).stream()
                        .collect(Collectors.toMap(AssetRepository.PropertyTotals::getPropertyId,
                                Function.identity()));
        Page<PropertyListItem> items = properties.map(p -> {
            AssetRepository.PropertyTotals t = totals.get(p.getId());
            return new PropertyListItem(p.getId(), p.getName(), p.getType(), p.getLocality(),
                    p.getGpsLat(), p.getGpsLng(), p.isOpenToOffers(),
                    t == null ? 0 : t.getAssetCount(),
                    t == null ? BigDecimal.ZERO : t.getTotalValue(),
                    p.getLastDocumentedAt(),
                    p.getOwnerUserId().equals(user.id()) ? "OWNER" : "MEMBER");
        });
        return PageEnvelope.of(items);
    }

    @Transactional(readOnly = true)
    public PropertyDetailResponse detail(AuthUser user, UUID propertyId) {
        Property property = accessService.requireMember(propertyId, user.id());
        AccessLevel access = accessService.accessTo(property, user.id());
        List<CategoryLine> byCategory = assetRepository.totalsByCategory(propertyId).stream()
                .map(t -> new CategoryLine(t.getCategory(), t.getAssetCount(), t.getTotalValue()))
                .toList();
        long assetCount = byCategory.stream().mapToLong(CategoryLine::count).sum();
        BigDecimal totalValue = byCategory.stream().map(CategoryLine::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PropertyDetailResponse(property.getId(), property.getName(), property.getType(),
                property.getLocality(), property.getGpsLat(), property.getGpsLng(),
                property.isOpenToOffers(), property.getOpenToOffersAt(), property.getLastDocumentedAt(),
                access.name(), property.getCreatedAt(),
                new Dashboard(assetCount, totalValue, byCategory));
    }

    @Transactional
    public PropertyDetailResponse update(AuthUser user, UUID propertyId, UpdatePropertyRequest request) {
        Property property = accessService.requireOwner(propertyId, user.id());
        if (request.name() != null && !request.name().isBlank()) {
            property.setName(request.name().trim());
        }
        if (request.type() != null) {
            property.setType(request.type());
        }
        if (request.gpsLat() != null) {
            property.setGpsLat(request.gpsLat());
        }
        if (request.gpsLng() != null) {
            property.setGpsLng(request.gpsLng());
        }
        if (request.locality() != null && !request.locality().isBlank()) {
            property.setLocality(request.locality().trim());
        }
        propertyRepository.save(property);
        return detail(user, propertyId);
    }

    /** Soft deletes the property and cascades to its assets and receipts. */
    @Transactional
    public DeleteResponse delete(AuthUser user, UUID propertyId) {
        accessService.requireOwner(propertyId, user.id());
        Instant now = Instant.now();
        // Receipts first: the asset subquery must still see live asset rows.
        receiptRepository.softDeleteByProperty(propertyId, now);
        assetRepository.softDeleteByProperty(propertyId, now);
        propertyRepository.softDelete(propertyId, now);
        return new DeleteResponse(true);
    }

    @Transactional
    public OptInResponse setOptIn(AuthUser user, UUID propertyId, OptInRequest request) {
        Property property = accessService.requireOwner(propertyId, user.id());
        boolean openToOffers = request.openToOffers();
        property.setOpenToOffers(openToOffers);
        property.setOpenToOffersAt(openToOffers ? Instant.now() : null);
        propertyRepository.save(property);
        marketplaceEvents.optInChanged(propertyId, openToOffers);
        return new OptInResponse(property.isOpenToOffers(), property.getOpenToOffersAt());
    }
}
