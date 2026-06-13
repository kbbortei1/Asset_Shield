package com.assetshield.property.access;

import com.assetshield.property.common.ApiException;
import com.assetshield.property.common.ErrorCode;
import com.assetshield.property.domain.Property;
import com.assetshield.property.repo.HouseholdMembershipRepository;
import com.assetshield.property.repo.PropertyRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single authorization point used by every endpoint.
 *
 * <pre>
 * Action                                   OWNER  MEMBER  MEMBER_EXPORT
 * view property/assets/receipts/dashboard    ✅      ✅        ✅
 * add assets / receipts                      ✅      ✅        ✅
 * edit/delete an asset                     any    own only   own only
 * property edit/delete, opt-in, members      ✅      ❌        ❌
 * </pre>
 *
 * Wrong access → 403 NOT_MEMBER / NOT_OWNER; unknown or soft-deleted
 * property → 404.
 */
@Service
public class PropertyAccessService {

    private final PropertyRepository propertyRepository;
    private final HouseholdMembershipRepository membershipRepository;

    public PropertyAccessService(PropertyRepository propertyRepository,
                                 HouseholdMembershipRepository membershipRepository) {
        this.propertyRepository = propertyRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public AccessLevel access(UUID propertyId, UUID userId) {
        return accessTo(requireProperty(propertyId), userId);
    }

    public AccessLevel accessTo(Property property, UUID userId) {
        if (property.getOwnerUserId().equals(userId)) {
            return AccessLevel.OWNER;
        }
        return membershipRepository
                .findByPropertyIdAndMemberUserIdAndRevokedAtIsNull(property.getId(), userId)
                .map(m -> m.isCanExport() ? AccessLevel.MEMBER_EXPORT : AccessLevel.MEMBER)
                .orElse(AccessLevel.NONE);
    }

    /** OWNER ∪ MEMBER: view + contribute. Returns the property for reuse. */
    @Transactional(readOnly = true)
    public Property requireMember(UUID propertyId, UUID userId) {
        Property property = requireProperty(propertyId);
        if (!accessTo(property, userId).canView()) {
            throw new ApiException(ErrorCode.NOT_MEMBER, "You are not a member of this property");
        }
        return property;
    }

    /** OWNER-only actions. Returns the property for reuse. */
    @Transactional(readOnly = true)
    public Property requireOwner(UUID propertyId, UUID userId) {
        Property property = requireProperty(propertyId);
        if (!accessTo(property, userId).isOwner()) {
            throw new ApiException(ErrorCode.NOT_OWNER, "Only the property owner can perform this action");
        }
        return property;
    }

    public Property requireProperty(UUID propertyId) {
        return propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Property not found"));
    }
}
