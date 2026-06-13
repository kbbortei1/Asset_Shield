package com.assetshield.damage.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetshield.damage.domain.AssetSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LossCalculatorTest {

    private record FakePair(UUID assetId, AssetSnapshot snapshot) {
    }

    private static FakePair pair(UUID assetId, String value) {
        return new FakePair(assetId, new AssetSnapshot("assets/x.jpg", "a".repeat(64), "thing",
                new BigDecimal(value), "OTHER", BigDecimal.ONE, BigDecimal.ONE, Instant.now()));
    }

    @Test
    void assetPairedWithTwoPhotosCountsOnce() {
        UUID tv = UUID.randomUUID();
        UUID sofa = UUID.randomUUID();
        List<FakePair> pairs = List.of(pair(tv, "3500.00"), pair(tv, "3500.00"), pair(sofa, "900.00"));

        BigDecimal total = LossCalculator.totalLoss(pairs, FakePair::assetId, FakePair::snapshot);

        assertThat(total).isEqualByComparingTo("4400.00");
    }

    @Test
    void noPairsMeansZeroLoss() {
        assertThat(LossCalculator.totalLoss(List.<FakePair>of(), FakePair::assetId, FakePair::snapshot))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void distinctAssetsAllCount() {
        List<FakePair> pairs = List.of(
                pair(UUID.randomUUID(), "100.00"),
                pair(UUID.randomUUID(), "200.50"),
                pair(UUID.randomUUID(), "0.00"));
        assertThat(LossCalculator.totalLoss(pairs, FakePair::assetId, FakePair::snapshot))
                .isEqualByComparingTo("300.50");
    }
}
