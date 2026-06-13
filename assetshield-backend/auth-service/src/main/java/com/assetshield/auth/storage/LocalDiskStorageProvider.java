package com.assetshield.auth.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Dev/offline-demo fallback (STORAGE_PROVIDER=local). Duplicated from
 * property-service — keep the two copies identical.
 */
public class LocalDiskStorageProvider implements StorageProvider {

    private final Path root;
    private final DownloadTokenStore tokenStore;

    public LocalDiskStorageProvider(String root, DownloadTokenStore tokenStore) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.tokenStore = tokenStore;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create local storage root " + this.root, e);
        }
    }

    @Override
    public String store(byte[] bytes, String objectPath, String contentType) {
        Path target = resolve(objectPath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write object " + objectPath, e);
        }
        return objectPath;
    }

    @Override
    public String signedUrl(String objectPath, Duration ttl) {
        return "/api/v1/public/files/" + tokenStore.issue(objectPath, ttl);
    }

    @Override
    public void delete(String objectPath) {
        try {
            Files.deleteIfExists(resolve(objectPath));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete object " + objectPath, e);
        }
    }

    /** Resolves an object path inside the root, rejecting path traversal. */
    public Path resolve(String objectPath) {
        Path target = root.resolve(objectPath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Object path escapes the storage root");
        }
        return target;
    }
}
