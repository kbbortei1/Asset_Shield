package com.assetshield.auth.storage;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory expiring token map backing the local storage provider's
 * "signed" URLs. Dev/offline use only. (Auth-service exposes no download
 * endpoint today — Ghana Cards are write-only via the API.)
 */
@Component
public class DownloadTokenStore {

    record Entry(String objectPath, Instant expiresAt) {
    }

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();

    public String issue(String objectPath, Duration ttl) {
        sweep();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new Entry(objectPath, Instant.now().plus(ttl)));
        return token;
    }

    /** Resolves a live token to its object path; empty when unknown or expired. */
    public Optional<String> resolve(String token) {
        Entry entry = tokens.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.objectPath());
    }

    private void sweep() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
