package com.assetshield.auth.storage;

import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Cloud storage on Supabase Storage (STORAGE_PROVIDER=supabase). Supabase
 * Storage is S3-compatible, so this uses the AWS S3 SDK v2 against the
 * project's S3 endpoint with path-style addressing (Supabase requires it).
 * Objects live in a single PRIVATE bucket; reads go through presigned GET
 * URLs minted locally (no extra round-trip). Fails fast at startup when any
 * required env is missing.
 *
 * Keep this class byte-identical across property/auth/damage (only the
 * package line differs) — the storage module is intentionally duplicated.
 */
public class SupabaseStorageProvider implements StorageProvider {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public SupabaseStorageProvider(String endpoint, String region, String accessKeyId,
                                   String secretAccessKey, String bucket) {
        require(endpoint, "SUPABASE_S3_ENDPOINT");
        require(region, "SUPABASE_S3_REGION");
        require(accessKeyId, "SUPABASE_S3_ACCESS_KEY_ID");
        require(secretAccessKey, "SUPABASE_S3_SECRET_ACCESS_KEY");
        require(bucket, "SUPABASE_STORAGE_BUCKET");
        this.bucket = bucket;

        URI endpointUri = URI.create(endpoint);
        Region awsRegion = Region.of(region);
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey));

        // Supabase only accepts path-style requests (bucket in the path, not the
        // host). The client exposes forcePathStyle directly; the presigner has
        // no such setter, so it takes the equivalent via S3Configuration. Set
        // path-style in exactly ONE place per builder — the SDK rejects both.
        this.s3 = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(awsRegion)
                .credentialsProvider(credentials)
                .forcePathStyle(true)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpointUri)
                .region(awsRegion)
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("STORAGE_PROVIDER=supabase but " + name + " is empty");
        }
    }

    @Override
    public String store(byte[] bytes, String objectPath, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectPath)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes));
        return objectPath;
    }

    @Override
    public String signedUrl(String objectPath, Duration ttl) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectPath)
                        .build())
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public void delete(String objectPath) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectPath)
                .build());
    }
}
