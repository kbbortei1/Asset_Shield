package com.assetshield.property.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * One storage contract — store, resolve a signed URL to the exact bytes,
 * delete, then fail to fetch — run against the LocalDiskStorageProvider
 * always, and against the real SupabaseStorageProvider only when
 * SUPABASE_S3_ENDPOINT is present (so it proves the real provider on demand
 * and skips cleanly in CI). No real credentials are required for the suite
 * to pass.
 */
class StorageProviderContractTest {

    /** Resolves a provider's signed URL to the stored bytes; throws if gone. */
    private interface Fetcher {
        byte[] fetch(String signedUrl) throws Exception;
    }

    private static void roundTrip(StorageProvider provider, String objectPath, Fetcher fetcher)
            throws Exception {
        byte[] input = ("contract-bytes-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);

        provider.store(input, objectPath, "application/octet-stream");

        String url = provider.signedUrl(objectPath, Duration.ofMinutes(15));
        assertThat(url).isNotBlank();
        assertThat(fetcher.fetch(url)).isEqualTo(input); // byte-for-byte

        provider.delete(objectPath);
        assertThatThrownBy(() -> fetcher.fetch(url))
                .as("fetch after delete must fail")
                .isInstanceOf(Exception.class);
    }

    @Test
    void localProviderRoundTrip(@TempDir java.nio.file.Path tempDir) throws Exception {
        DownloadTokenStore tokenStore = new DownloadTokenStore();
        LocalDiskStorageProvider provider =
                new LocalDiskStorageProvider(tempDir.toString(), tokenStore);
        String objectPath = "assets/" + UUID.randomUUID() + "/photo.bin";

        // the local "signed URL" is /api/v1/public/files/{token}; resolving the
        // token to the object path and reading the file off disk mirrors what
        // PublicFileController streams to clients
        Fetcher fetcher = url -> {
            String token = url.substring(url.lastIndexOf('/') + 1);
            String resolved = tokenStore.resolve(token)
                    .orElseThrow(() -> new IllegalStateException("token expired or unknown"));
            return Files.readAllBytes(provider.resolve(resolved));
        };
        roundTrip(provider, objectPath, fetcher);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SUPABASE_S3_ENDPOINT", matches = ".+")
    void supabaseProviderRoundTrip() throws Exception {
        SupabaseStorageProvider provider = new SupabaseStorageProvider(
                System.getenv("SUPABASE_S3_ENDPOINT"),
                System.getenv("SUPABASE_S3_REGION"),
                System.getenv("SUPABASE_S3_ACCESS_KEY_ID"),
                System.getenv("SUPABASE_S3_SECRET_ACCESS_KEY"),
                envOrDefault("SUPABASE_STORAGE_BUCKET", "assetshield"));
        String objectPath = "contract-tests/" + UUID.randomUUID() + ".bin";

        HttpClient http = HttpClient.newHttpClient();
        Fetcher fetcher = url -> {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("GET returned " + response.statusCode());
            }
            return response.body();
        };
        roundTrip(provider, objectPath, fetcher);
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
