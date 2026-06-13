package com.assetshield.damage.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetshield.damage.TestProps;
import com.assetshield.damage.domain.AssetSnapshot;
import com.assetshield.damage.domain.DamagePhoto;
import com.assetshield.damage.domain.DamageReport;
import com.assetshield.damage.domain.DisasterType;
import com.assetshield.damage.domain.PairingMethod;
import com.assetshield.damage.domain.PhotoPair;
import com.assetshield.damage.service.SnapshotMapper;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the Flyway schema: photo-hash partial unique index, pair
 * uniqueness, and exact JSONB snapshot round-trips.
 *
 * NOTE: an expected constraint violation aborts the test's Postgres
 * transaction, so each test puts the violation LAST.
 */
@DataJpaTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DamageSchemaIT {

    private static final String HASH_A = "a".repeat(64);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    DamageReportRepository reportRepository;

    @Autowired
    DamagePhotoRepository photoRepository;

    @Autowired
    PhotoPairRepository pairRepository;

    private final SnapshotMapper snapshotMapper = new SnapshotMapper(new ObjectMapper());

    private DamageReport report() {
        DamageReport r = new DamageReport();
        r.setPropertyId(UUID.randomUUID());
        r.setCreatedByUserId(UUID.randomUUID());
        r.setDisasterType(DisasterType.FIRE);
        r.setOccurredAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return reportRepository.saveAndFlush(r);
    }

    private DamagePhoto photo(UUID reportId, String hash) {
        DamagePhoto p = new DamagePhoto();
        p.setDamageReportId(reportId);
        p.setPhotoUrl("damage/" + reportId + "/" + hash + ".jpg");
        p.setSha256Hash(hash);
        p.setGpsLat(new BigDecimal("5.546111"));
        p.setGpsLng(new BigDecimal("-0.211667"));
        p.setCapturedAt(Instant.now());
        return p;
    }

    private PhotoPair pair(UUID reportId, UUID photoId, UUID assetId) {
        PhotoPair pair = new PhotoPair();
        pair.setDamageReportId(reportId);
        pair.setDamagePhotoId(photoId);
        pair.setAssetId(assetId);
        pair.setPairingMethod(PairingMethod.GPS_AUTO);
        pair.setDistanceMeters(new BigDecimal("12.34"));
        pair.setAssetSnapshot(snapshotMapper.toJson(new AssetSnapshot(
                "assets/p/a.jpg", "b".repeat(64), "TV", new BigDecimal("3500.00"),
                "ELECTRONICS", new BigDecimal("5.546111"), new BigDecimal("-0.211667"),
                Instant.parse("2026-06-01T08:00:00Z"))));
        return pair;
    }

    @Test
    void duplicateLivePhotoHashRejectedButReinsertAfterSoftDeleteWorks() {
        DamageReport r = report();
        DamagePhoto original = photoRepository.saveAndFlush(photo(r.getId(), HASH_A));

        // soft delete frees the (report, hash) slot …
        original.setDeletedAt(Instant.now());
        photoRepository.saveAndFlush(original);
        DamagePhoto reInserted = photoRepository.saveAndFlush(photo(r.getId(), HASH_A));
        assertThat(reInserted.getId()).isNotEqualTo(original.getId());

        // … but a second LIVE row with the same hash violates the partial index
        assertThatThrownBy(() -> photoRepository.saveAndFlush(photo(r.getId(), HASH_A)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_dmgphoto_report_hash");
    }

    @Test
    void onePairPerPhotoAssetCombination() {
        DamageReport r = report();
        DamagePhoto p = photoRepository.saveAndFlush(photo(r.getId(), HASH_A));
        UUID assetId = UUID.randomUUID();
        pairRepository.saveAndFlush(pair(r.getId(), p.getId(), assetId));

        // a different asset on the same photo is fine
        pairRepository.saveAndFlush(pair(r.getId(), p.getId(), UUID.randomUUID()));

        assertThatThrownBy(() -> pairRepository.saveAndFlush(pair(r.getId(), p.getId(), assetId)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_pair_unique");
    }

    @Test
    void jsonbSnapshotPersistsAndReadsBackExactly() {
        DamageReport r = report();
        DamagePhoto p = photoRepository.saveAndFlush(photo(r.getId(), HASH_A));
        PhotoPair saved = pairRepository.saveAndFlush(pair(r.getId(), p.getId(), UUID.randomUUID()));

        PhotoPair reloaded = pairRepository.findById(saved.getId()).orElseThrow();
        AssetSnapshot snapshot = snapshotMapper.fromJson(reloaded.getAssetSnapshot());

        assertThat(snapshot.objectPath()).isEqualTo("assets/p/a.jpg");
        assertThat(snapshot.sha256Hash()).isEqualTo("b".repeat(64));
        assertThat(snapshot.estimatedValue()).isEqualByComparingTo("3500.00");
        assertThat(snapshot.category()).isEqualTo("ELECTRONICS");
        assertThat(snapshot.capturedAt()).isEqualTo(Instant.parse("2026-06-01T08:00:00Z"));
    }
}
