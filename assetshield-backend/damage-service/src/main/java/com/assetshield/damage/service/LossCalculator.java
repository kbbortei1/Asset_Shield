package com.assetshield.damage.service;

import com.assetshield.damage.domain.AssetSnapshot;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * totalEstimatedLoss = sum of snapshot estimatedValue over DISTINCT assets —
 * an asset paired with several damage photos counts exactly once.
 */
public final class LossCalculator {

    private LossCalculator() {
    }

    public static <P> BigDecimal totalLoss(Iterable<P> pairs, Function<P, UUID> assetId,
                                           Function<P, AssetSnapshot> snapshot) {
        Map<UUID, BigDecimal> byAsset = new LinkedHashMap<>();
        for (P pair : pairs) {
            byAsset.putIfAbsent(assetId.apply(pair), snapshot.apply(pair).estimatedValue());
        }
        return byAsset.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
