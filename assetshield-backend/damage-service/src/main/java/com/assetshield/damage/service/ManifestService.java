package com.assetshield.damage.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Deterministic dossier manifest.
 *
 * <p><b>Algorithm (exact):</b>
 * <ol>
 *   <li>the SHA-256 of every <b>distinct paired asset</b> snapshot, ordered by
 *       assetId ascending (UUID string form, lexicographic);</li>
 *   <li>the SHA-256 of every damage photo, ordered by photo id ascending
 *       (UUID string form, lexicographic).</li>
 * </ol>
 * Join the lowercase hex strings with {@code \n}, encode UTF-8, SHA-256 the
 * result — that is the {@code manifest_hash}. Verification recomputes the
 * same and additionally re-downloads each stored object and re-hashes its
 * bytes.
 */
@Service
public class ManifestService {

    /** One manifest input line: where it came from and its sha256. */
    public record Entry(String label, UUID id, String sha256) {
    }

    public String manifestHash(Map<UUID, String> assetHashesByAssetId,
                               Map<UUID, String> photoHashesByPhotoId) {
        return Sha256.hex(String.join("\n", orderedHashes(assetHashesByAssetId, photoHashesByPhotoId))
                .getBytes(StandardCharsets.UTF_8));
    }

    /** The exact ordered list of lowercase hex inputs (also used for the PDF manifest page). */
    public List<String> orderedHashes(Map<UUID, String> assetHashesByAssetId,
                                      Map<UUID, String> photoHashesByPhotoId) {
        List<String> hashes = new ArrayList<>();
        sortedByUuidString(assetHashesByAssetId).values()
                .forEach(hash -> hashes.add(hash.toLowerCase()));
        sortedByUuidString(photoHashesByPhotoId).values()
                .forEach(hash -> hashes.add(hash.toLowerCase()));
        return hashes;
    }

    /** Labelled entries in manifest order, for the PDF manifest page and verification. */
    public List<Entry> orderedEntries(Map<UUID, String> assetHashesByAssetId,
                                      Map<UUID, String> photoHashesByPhotoId) {
        List<Entry> entries = new ArrayList<>();
        sortedByUuidString(assetHashesByAssetId)
                .forEach((id, hash) -> entries.add(new Entry("ASSET", id, hash.toLowerCase())));
        sortedByUuidString(photoHashesByPhotoId)
                .forEach((id, hash) -> entries.add(new Entry("PHOTO", id, hash.toLowerCase())));
        return entries;
    }

    private static TreeMap<UUID, String> sortedByUuidString(Map<UUID, String> input) {
        TreeMap<UUID, String> sorted = new TreeMap<>(Comparator.comparing(UUID::toString));
        sorted.putAll(input);
        return sorted;
    }
}
