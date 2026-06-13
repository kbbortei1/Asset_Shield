package com.assetshield.auth.storage;

import java.time.Duration;

/**
 * Object storage abstraction (duplicated from property-service — independent
 * poms, keep the two copies identical). The database stores object paths
 * only — never URLs.
 */
public interface StorageProvider {

    /** Stores the bytes and returns the stored object path. */
    String store(byte[] bytes, String objectPath, String contentType);

    /** Returns a time-limited download URL for a stored object. */
    String signedUrl(String objectPath, Duration ttl);

    void delete(String objectPath);
}
