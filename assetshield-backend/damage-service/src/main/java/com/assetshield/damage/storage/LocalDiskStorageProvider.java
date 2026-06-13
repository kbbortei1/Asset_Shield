package com.assetshield.damage.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Dev/offline-demo fallback (STORAGE_PROVIDER=local). Duplicated from
 * property-service with ONE deliberate difference: the download path is
 * /api/v1/public/damage-files/{token} so the gateway can route damage-service
 * downloads here instead of to property-service (token maps are in-memory and
 * per-service). The storage volume itself is shared, so asset object paths
 * frozen in pair snapshots resolve here too.
 */
public class LocalDiskStorageProvider implements StorageProvider {

    public static final String PUBLIC_PATH = "/api/v1/public/damage-files/";

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
    public byte[] load(String objectPath) {
        try {
            return Files.readAllBytes(resolve(objectPath));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read object " + objectPath, e);
        }
    }

    @Override
    public String signedUrl(String objectPath, Duration ttl) {
        return PUBLIC_PATH + tokenStore.issue(objectPath, ttl);
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
