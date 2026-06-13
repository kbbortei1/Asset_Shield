package com.assetshield.damage.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManifestServiceTest {

    private final ManifestService manifestService = new ManifestService();

    private static final UUID ASSET_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ASSET_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PHOTO_1 = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PHOTO_2 = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID PHOTO_3 = UUID.fromString("10000000-0000-0000-0000-000000000003");

    private static final String HA = "a".repeat(64);
    private static final String HB = "b".repeat(64);
    private static final String H1 = "1".repeat(64);
    private static final String H2 = "2".repeat(64);
    private static final String H3 = "3".repeat(64);

    @Test
    void manifestMatchesTheDocumentedAlgorithmExactly() {
        // assets ordered by id asc, then photos ordered by id asc, joined with \n
        String joined = String.join("\n", HA, HB, H1, H2, H3);
        String expected = Sha256.hex(joined.getBytes(StandardCharsets.UTF_8));

        String actual = manifestService.manifestHash(
                Map.of(ASSET_A, HA, ASSET_B, HB),
                Map.of(PHOTO_1, H1, PHOTO_2, H2, PHOTO_3, H3));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void hashIsByteIdenticalRegardlessOfInputOrder() {
        Map<UUID, String> assetsForward = new LinkedHashMap<>();
        assetsForward.put(ASSET_A, HA);
        assetsForward.put(ASSET_B, HB);
        Map<UUID, String> assetsReversed = new LinkedHashMap<>();
        assetsReversed.put(ASSET_B, HB);
        assetsReversed.put(ASSET_A, HA);

        Map<UUID, String> photosForward = new LinkedHashMap<>();
        photosForward.put(PHOTO_1, H1);
        photosForward.put(PHOTO_2, H2);
        photosForward.put(PHOTO_3, H3);
        Map<UUID, String> photosReversed = new LinkedHashMap<>();
        photosReversed.put(PHOTO_3, H3);
        photosReversed.put(PHOTO_1, H1);
        photosReversed.put(PHOTO_2, H2);

        String first = manifestService.manifestHash(assetsForward, photosForward);
        String second = manifestService.manifestHash(assetsReversed, photosReversed);
        String third = manifestService.manifestHash(assetsReversed, photosForward);

        assertThat(first).isEqualTo(second).isEqualTo(third);
    }

    @Test
    void orderedEntriesListAssetsBeforePhotos() {
        List<ManifestService.Entry> entries = manifestService.orderedEntries(
                Map.of(ASSET_B, HB, ASSET_A, HA), Map.of(PHOTO_2, H2, PHOTO_1, H1));
        assertThat(entries).extracting(ManifestService.Entry::label)
                .containsExactly("ASSET", "ASSET", "PHOTO", "PHOTO");
        assertThat(entries).extracting(ManifestService.Entry::id)
                .containsExactly(ASSET_A, ASSET_B, PHOTO_1, PHOTO_2);
    }

    @Test
    void uppercaseInputHashesAreNormalizedToLowercase() {
        String upper = manifestService.manifestHash(Map.of(ASSET_A, HA.toUpperCase()), Map.of());
        String lower = manifestService.manifestHash(Map.of(ASSET_A, HA), Map.of());
        assertThat(upper).isEqualTo(lower);
    }
}
