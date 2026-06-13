package com.assetshield.property.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetshield.property.TestProps;
import com.assetshield.property.domain.Asset;
import com.assetshield.property.domain.AssetCategory;
import com.assetshield.property.domain.AssetReceipt;
import com.assetshield.property.domain.HouseholdInvitation;
import com.assetshield.property.domain.HouseholdMembership;
import com.assetshield.property.domain.InvitationStatus;
import com.assetshield.property.domain.Property;
import com.assetshield.property.domain.PropertyType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the Flyway schema: partial unique indexes (live duplicates rejected,
 * re-insert after soft delete / status change / revoke allowed) and the
 * cascade soft-delete bulk updates.
 */
@DataJpaTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PropertySchemaIT {

    private static final String HASH_A = "a".repeat(64);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    PropertyRepository propertyRepository;

    @Autowired
    AssetRepository assetRepository;

    @Autowired
    AssetReceiptRepository receiptRepository;

    @Autowired
    HouseholdInvitationRepository invitationRepository;

    @Autowired
    HouseholdMembershipRepository membershipRepository;

    private Property property() {
        Property p = new Property();
        p.setOwnerUserId(UUID.randomUUID());
        p.setName("Adabraka Flat");
        p.setType(PropertyType.RESIDENTIAL);
        p.setGpsLat(new BigDecimal("5.603700"));
        p.setGpsLng(new BigDecimal("-0.187000"));
        p.setLocality("Adabraka");
        return propertyRepository.saveAndFlush(p);
    }

    private Asset asset(UUID propertyId, String hash) {
        Asset a = new Asset();
        a.setPropertyId(propertyId);
        a.setCreatedByUserId(UUID.randomUUID());
        a.setPhotoUrl("assets/" + propertyId + "/" + hash + ".jpg");
        a.setSha256Hash(hash);
        a.setGpsLat(new BigDecimal("5.603700"));
        a.setGpsLng(new BigDecimal("-0.187000"));
        a.setCapturedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        a.setDescription("Samsung TV");
        a.setEstimatedValue(new BigDecimal("3500.00"));
        a.setCategory(AssetCategory.ELECTRONICS);
        return a;
    }

    // NOTE: an expected constraint violation aborts the test's Postgres
    // transaction, so each test puts the violation LAST.

    @Test
    void duplicateLiveAssetHashIsRejectedButReinsertAfterSoftDeleteWorks() {
        Property p = property();
        Asset original = assetRepository.saveAndFlush(asset(p.getId(), HASH_A));

        // soft delete frees the (property, hash) slot …
        original.setDeletedAt(Instant.now());
        assetRepository.saveAndFlush(original);
        Asset reInserted = assetRepository.saveAndFlush(asset(p.getId(), HASH_A));
        assertThat(reInserted.getId()).isNotEqualTo(original.getId());

        // … but a second LIVE row with the same hash violates the partial index
        assertThatThrownBy(() -> assetRepository.saveAndFlush(asset(p.getId(), HASH_A)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_assets_property_hash");
    }

    @Test
    void sameHashOnAnotherPropertyIsAllowed() {
        Asset first = assetRepository.saveAndFlush(asset(property().getId(), HASH_A));
        Asset second = assetRepository.saveAndFlush(asset(property().getId(), HASH_A));
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void onePendingInvitePerPhonePerProperty() {
        Property p = property();
        HouseholdInvitation first = invitationRepository
                .saveAndFlush(invitation(p.getId(), "+233244111222"));

        // Once the invite leaves PENDING the phone can be re-invited …
        first.setStatus(InvitationStatus.DECLINED);
        invitationRepository.saveAndFlush(first);
        assertThat(invitationRepository
                .saveAndFlush(invitation(p.getId(), "+233244111222")).getId()).isNotNull();

        // … but a second PENDING invite for the same phone is rejected
        assertThatThrownBy(() ->
                invitationRepository.saveAndFlush(invitation(p.getId(), "+233244111222")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_invite_pending");
    }

    @Test
    void oneActiveMembershipPerUserPerProperty() {
        Property p = property();
        UUID member = UUID.randomUUID();
        HouseholdMembership first = membershipRepository.saveAndFlush(membership(p.getId(), member));

        // revoking frees the slot for a fresh membership …
        first.setRevokedAt(Instant.now());
        membershipRepository.saveAndFlush(first);
        assertThat(membershipRepository
                .saveAndFlush(membership(p.getId(), member)).getId()).isNotNull();

        // … but a second ACTIVE membership is rejected
        assertThatThrownBy(() -> membershipRepository.saveAndFlush(membership(p.getId(), member)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_membership_active");
    }

    @Test
    void cascadeSoftDeleteHidesAssetsAndReceipts() {
        Property p = property();
        Asset a = assetRepository.saveAndFlush(asset(p.getId(), HASH_A));
        AssetReceipt r = new AssetReceipt();
        r.setAssetId(a.getId());
        r.setReceiptUrl("receipts/" + a.getId() + "/" + "b".repeat(64) + ".jpg");
        r.setSha256Hash("b".repeat(64));
        r.setUploadedByUserId(UUID.randomUUID());
        receiptRepository.saveAndFlush(r);

        Instant now = Instant.now();
        assertThat(receiptRepository.softDeleteByProperty(p.getId(), now)).isEqualTo(1);
        assertThat(assetRepository.softDeleteByProperty(p.getId(), now)).isEqualTo(1);
        assertThat(propertyRepository.softDelete(p.getId(), now)).isEqualTo(1);

        assertThat(propertyRepository.findByIdAndDeletedAtIsNull(p.getId())).isEmpty();
        assertThat(assetRepository.findByIdAndDeletedAtIsNull(a.getId())).isEmpty();
        assertThat(receiptRepository.findByAssetIdAndDeletedAtIsNullOrderByCreatedAtAsc(a.getId()))
                .isEmpty();
        assertThat(assetRepository.countByPropertyIdAndDeletedAtIsNull(p.getId())).isZero();
    }

    private HouseholdInvitation invitation(UUID propertyId, String phone) {
        HouseholdInvitation i = new HouseholdInvitation();
        i.setPropertyId(propertyId);
        i.setInvitedByUserId(UUID.randomUUID());
        i.setInviteePhone(phone);
        i.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        return i;
    }

    private HouseholdMembership membership(UUID propertyId, UUID memberUserId) {
        HouseholdMembership m = new HouseholdMembership();
        m.setPropertyId(propertyId);
        m.setMemberUserId(memberUserId);
        m.setGrantedByUserId(UUID.randomUUID());
        return m;
    }
}
