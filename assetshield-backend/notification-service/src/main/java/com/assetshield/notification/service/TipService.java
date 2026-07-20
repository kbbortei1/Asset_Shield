package com.assetshield.notification.service;

import com.assetshield.notification.client.PropertyClient;
import com.assetshield.notification.common.ApiException;
import com.assetshield.notification.common.ErrorCode;
import com.assetshield.notification.common.PageEnvelope;
import com.assetshield.notification.domain.Tip;
import com.assetshield.notification.repo.TipRepository;
import com.assetshield.notification.security.AuthUser;
import com.assetshield.notification.web.dto.NotificationDtos.TipItem;
import com.assetshield.notification.web.dto.NotificationDtos.TipReadResponse;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TipService {

    private static final Set<String> VIEW_ACCESS = Set.of("OWNER", "MEMBER_EXPORT", "MEMBER");

    private final TipRepository tipRepository;
    private final PropertyClient propertyClient;

    public TipService(TipRepository tipRepository, PropertyClient propertyClient) {
        this.tipRepository = tipRepository;
        this.propertyClient = propertyClient;
    }

    @Transactional(readOnly = true)
    public PageEnvelope<TipItem> feed(AuthUser user, int page, int size) {
        return PageEnvelope.of(tipRepository
                .findByUserIdOrderByCreatedAtDesc(user.id(),
                        PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)))
                .map(TipService::toItem));
    }

    /** Household members can read a property's tips too (access via property). */
    @Transactional(readOnly = true)
    public PageEnvelope<TipItem> forProperty(AuthUser user, UUID propertyId, int page, int size) {
        if (!VIEW_ACCESS.contains(propertyClient.access(propertyId, user.id()))) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Property not found");
        }
        // tips belong to the owner; the property page shows them to any member
        return PageEnvelope.of(tipRepository
                .findByUserIdAndPropertyIdOrderByCreatedAtDesc(ownerOf(propertyId), propertyId,
                        PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)))
                .map(TipService::toItem));
    }

    private UUID ownerOf(UUID propertyId) {
        return propertyClient.tipsContext(propertyId)
                .map(PropertyClient.TipsContext::ownerUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Property not found"));
    }

    /** Foreign tip is a 404, not a 403 — tips must not be enumerable. */
    @Transactional
    public TipReadResponse markRead(AuthUser user, UUID tipId) {
        Tip tip = tipRepository.findById(tipId)
                .filter(found -> found.getUserId().equals(user.id()))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Tip not found"));
        if (tip.getReadAt() == null) { // idempotent: first read wins
            // truncate to what PostgreSQL stores (micros) so the value returned
            // from memory now and from the DB later are identical — Postgres
            // ROUNDS nanoseconds, which made the two differ by 1µs half the time
            tip.setReadAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            tipRepository.save(tip);
        }
        return new TipReadResponse(tip.getReadAt());
    }

    private static TipItem toItem(Tip tip) {
        return new TipItem(tip.getId(), tip.getPropertyId(), tip.getTipText(), tip.getCategory(),
                tip.getCreatedAt(), tip.getReadAt());
    }
}
