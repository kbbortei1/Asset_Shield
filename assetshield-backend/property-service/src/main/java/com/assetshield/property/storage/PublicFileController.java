package com.assetshield.property.storage;

import com.assetshield.property.common.ApiException;
import com.assetshield.property.common.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Streams local-provider downloads while their token is live (permitAll —
 * possession of an unexpired token IS the authorization, mirroring how a
 * signed URL works). With the supabase provider no tokens are ever issued
 * (reads use presigned S3 URLs), so every request here 404s.
 */
@RestController
@RequestMapping("/api/v1/public/files")
public class PublicFileController {

    private final DownloadTokenStore tokenStore;
    private final StorageProvider storageProvider;

    public PublicFileController(DownloadTokenStore tokenStore, StorageProvider storageProvider) {
        this.tokenStore = tokenStore;
        this.storageProvider = storageProvider;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Resource> download(@PathVariable String token) {
        String objectPath = tokenStore.resolve(token)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Download link is invalid or has expired"));
        if (!(storageProvider instanceof LocalDiskStorageProvider local)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Download link is invalid or has expired");
        }
        Path file = local.resolve(objectPath);
        if (!Files.isReadable(file)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Download link is invalid or has expired");
        }
        return ResponseEntity.ok()
                .contentType(mediaTypeFor(objectPath))
                .body(new FileSystemResource(file));
    }

    private static MediaType mediaTypeFor(String objectPath) {
        String lower = objectPath.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
