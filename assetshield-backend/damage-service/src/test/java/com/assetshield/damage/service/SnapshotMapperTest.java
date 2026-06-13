package com.assetshield.damage.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetshield.damage.domain.AssetSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SnapshotMapperTest {

    private final SnapshotMapper mapper = new SnapshotMapper(new ObjectMapper());

    @Test
    void snapshotRoundTripsExactly() {
        AssetSnapshot original = new AssetSnapshot(
                "assets/3f0e/abc.jpg",
                "f".repeat(64),
                "Samsung 55\" TV — sitting room",
                new BigDecimal("3500.00"),
                "ELECTRONICS",
                new BigDecimal("5.546111"),
                new BigDecimal("-0.211667"),
                Instant.parse("2026-06-10T10:15:30Z"));

        AssetSnapshot back = mapper.fromJson(mapper.toJson(original));

        assertThat(back).isEqualTo(original);
    }

    @Test
    void jsonContainsTheFrozenFields() {
        String json = mapper.toJson(new AssetSnapshot("p", "h", "d", BigDecimal.TEN, "OTHER",
                BigDecimal.ONE, BigDecimal.ONE, Instant.parse("2026-01-01T00:00:00Z")));
        assertThat(json).contains("\"objectPath\"", "\"sha256Hash\"", "\"estimatedValue\"",
                "\"category\"", "\"capturedAt\"");
    }
}
