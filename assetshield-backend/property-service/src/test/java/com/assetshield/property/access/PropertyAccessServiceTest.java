package com.assetshield.property.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetshield.property.common.ApiException;
import com.assetshield.property.common.ErrorCode;
import com.assetshield.property.domain.HouseholdMembership;
import com.assetshield.property.domain.Property;
import com.assetshield.property.repo.HouseholdMembershipRepository;
import com.assetshield.property.repo.PropertyRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The whole permission matrix lives in one service — verified level by level. */
class PropertyAccessServiceTest {

    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final HouseholdMembershipRepository membershipRepository =
            mock(HouseholdMembershipRepository.class);
    private final PropertyAccessService accessService =
            new PropertyAccessService(propertyRepository, membershipRepository);

    private final UUID propertyId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID exporterId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();

    private Property property;

    @BeforeEach
    void setUp() {
        property = new Property();
        ReflectionTestUtils.setField(property, "id", propertyId);
        property.setOwnerUserId(ownerId);
        when(propertyRepository.findByIdAndDeletedAtIsNull(propertyId))
                .thenReturn(Optional.of(property));
        when(membershipRepository.findByPropertyIdAndMemberUserIdAndRevokedAtIsNull(any(), any()))
                .thenReturn(Optional.empty());
        when(membershipRepository.findByPropertyIdAndMemberUserIdAndRevokedAtIsNull(
                eq(propertyId), eq(memberId)))
                .thenReturn(Optional.of(membership(false)));
        when(membershipRepository.findByPropertyIdAndMemberUserIdAndRevokedAtIsNull(
                eq(propertyId), eq(exporterId)))
                .thenReturn(Optional.of(membership(true)));
    }

    private HouseholdMembership membership(boolean canExport) {
        HouseholdMembership m = new HouseholdMembership();
        m.setPropertyId(propertyId);
        m.setCanExport(canExport);
        return m;
    }

    @Test
    void resolvesAllFourAccessLevels() {
        assertThat(accessService.access(propertyId, ownerId)).isEqualTo(AccessLevel.OWNER);
        assertThat(accessService.access(propertyId, memberId)).isEqualTo(AccessLevel.MEMBER);
        assertThat(accessService.access(propertyId, exporterId)).isEqualTo(AccessLevel.MEMBER_EXPORT);
        assertThat(accessService.access(propertyId, strangerId)).isEqualTo(AccessLevel.NONE);
    }

    @Test
    void viewAndContributeAllowedForOwnerAndBothMemberKinds() {
        assertThat(accessService.requireMember(propertyId, ownerId)).isSameAs(property);
        assertThat(accessService.requireMember(propertyId, memberId)).isSameAs(property);
        assertThat(accessService.requireMember(propertyId, exporterId)).isSameAs(property);
    }

    @Test
    void strangerIsRejectedWithNotMember() {
        assertThatThrownBy(() -> accessService.requireMember(propertyId, strangerId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_MEMBER));
    }

    @Test
    void ownerOnlyActionsRejectMembersWithNotOwner() {
        assertThat(accessService.requireOwner(propertyId, ownerId)).isSameAs(property);
        for (UUID userId : new UUID[]{memberId, exporterId, strangerId}) {
            assertThatThrownBy(() -> accessService.requireOwner(propertyId, userId))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_OWNER));
        }
    }

    @Test
    void unknownOrSoftDeletedPropertyIs404() {
        UUID missing = UUID.randomUUID();
        when(propertyRepository.findByIdAndDeletedAtIsNull(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accessService.access(missing, ownerId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void revokedMembershipMeansNone() {
        when(membershipRepository.findByPropertyIdAndMemberUserIdAndRevokedAtIsNull(
                eq(propertyId), eq(memberId)))
                .thenReturn(Optional.empty());
        assertThat(accessService.access(propertyId, memberId)).isEqualTo(AccessLevel.NONE);
    }
}
