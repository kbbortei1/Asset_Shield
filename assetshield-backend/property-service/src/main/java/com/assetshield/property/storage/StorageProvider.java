package com.assetshield.property.storage;

import java.time.Duration;

/**
 * Object storage abstraction. The database stores object paths only — never
 * URLs. DTO mappers call {@link #signedUrl} at read time.
 */
public interface StorageProvider {

    /** Stores the bytes and returns the stored object path. */
    String store(byte[] bytes, String objectPath, String contentType);

    /** Returns a time-limited download URL for a stored object. */
    String signedUrl(String objectPath, Duration ttl);

    void delete(String objectPath);
}
