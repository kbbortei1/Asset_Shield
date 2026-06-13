package com.assetshield.damage.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage.SignUrlOption;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Production storage (STORAGE_PROVIDER=firebase). Objects are private; reads
 * go through V4 signed URLs. Fails fast at startup when the service-account
 * file is missing.
 */
public class FirebaseStorageProvider implements StorageProvider {

    private final String bucketName;

    public FirebaseStorageProvider(String serviceAccountPath, String bucketName) {
        if (serviceAccountPath == null || serviceAccountPath.isBlank()
                || !Files.isReadable(Path.of(serviceAccountPath))) {
            throw new IllegalStateException(
                    "STORAGE_PROVIDER=firebase but FIREBASE_SERVICE_ACCOUNT_PATH does not point "
                            + "to a readable file: " + serviceAccountPath);
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("STORAGE_PROVIDER=firebase but FIREBASE_STORAGE_BUCKET is empty");
        }
        this.bucketName = bucketName;
        if (FirebaseApp.getApps().isEmpty()) {
            try (FileInputStream credentials = new FileInputStream(serviceAccountPath)) {
                FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentials))
                        .setStorageBucket(bucketName)
                        .build());
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot initialize Firebase from " + serviceAccountPath, e);
            }
        }
    }

    private Bucket bucket() {
        return StorageClient.getInstance().bucket(bucketName);
    }

    @Override
    public String store(byte[] bytes, String objectPath, String contentType) {
        bucket().create(objectPath, bytes, contentType);
        return objectPath;
    }

    @Override
    public byte[] load(String objectPath) {
        Blob blob = bucket().get(objectPath);
        if (blob == null) {
            throw new IllegalStateException("Object not found in bucket: " + objectPath);
        }
        return blob.getContent();
    }

    @Override
    public String signedUrl(String objectPath, Duration ttl) {
        Blob blob = bucket().get(objectPath);
        if (blob == null) {
            throw new IllegalStateException("Object not found in bucket: " + objectPath);
        }
        return blob.signUrl(ttl.toSeconds(), TimeUnit.SECONDS, SignUrlOption.withV4Signature()).toString();
    }

    @Override
    public void delete(String objectPath) {
        Blob blob = bucket().get(objectPath);
        if (blob != null) {
            blob.delete();
        }
    }
}
