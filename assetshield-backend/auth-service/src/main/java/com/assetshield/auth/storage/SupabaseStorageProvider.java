package com.assetshield.auth.storage;

import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Cloud storage on Supabase Storage (STORAGE_PROVIDER=supabase). Duplicated
 * from property-service — keep the two copies identical. Objects live in a
 * PRIVATE bucket; reads go through time-limited signed URLs minted with the
 * service-role key. Fails fast at startup when config is missing.
 */
public class SupabaseStorageProvider implements StorageProvider {

    private final RestClient restClient;
    private final String projectUrl;
    private final String bucket;

    public SupabaseStorageProvider(String projectUrl, String serviceKey, String bucket) {
        if (projectUrl == null || projectUrl.isBlank()) {
            throw new IllegalStateException("STORAGE_PROVIDER=supabase but SUPABASE_URL is empty");
        }
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("STORAGE_PROVIDER=supabase but SUPABASE_SERVICE_KEY is empty");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("STORAGE_PROVIDER=supabase but SUPABASE_STORAGE_BUCKET is empty");
        }
        this.projectUrl = projectUrl.replaceAll("/+$", "");
        this.bucket = bucket;
        this.restClient = RestClient.builder()
                .baseUrl(this.projectUrl + "/storage/v1")
                .defaultHeader("Authorization", "Bearer " + serviceKey)
                .defaultHeader("apikey", serviceKey)
                .build();
    }

    @Override
    public String store(byte[] bytes, String objectPath, String contentType) {
        // x-upsert: re-storing the same path (same content hash) must not fail
        restClient.post()
                .uri("/object/" + bucket + "/" + objectPath)
                .header("x-upsert", "true")
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes)
                .retrieve()
                .toBodilessEntity();
        return objectPath;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String signedUrl(String objectPath, Duration ttl) {
        Map<String, Object> response = restClient.post()
                .uri("/object/sign/" + bucket + "/" + objectPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", ttl.toSeconds()))
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("signedURL") == null) {
            throw new IllegalStateException("Supabase did not return a signed URL for " + objectPath);
        }
        // Supabase returns a path relative to /storage/v1
        return projectUrl + "/storage/v1" + response.get("signedURL");
    }

    @Override
    public void delete(String objectPath) {
        restClient.delete()
                .uri("/object/" + bucket + "/" + objectPath)
                .retrieve()
                .toBodilessEntity();
    }
}
