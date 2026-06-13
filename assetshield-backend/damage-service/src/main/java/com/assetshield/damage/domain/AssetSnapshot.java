package com.assetshield.damage.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The shape frozen into photo_pairs.asset_snapshot at pairing time. This is
 * the ONLY source for the pair's "before" block from then on.
 */
public record AssetSnapshot(
        String objectPath,
        String sha256Hash,
        String description,
        BigDecimal estimatedValue,
        String category,
        BigDecimal gpsLat,
        BigDecimal gpsLng,
        Instant capturedAt) {
}
