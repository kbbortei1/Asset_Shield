package com.assetshield.auth.storage;

import com.assetshield.auth.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects the storage backend by env STORAGE_PROVIDER=supabase|local. */
@Configuration
public class StorageConfig {

    @Bean
    public StorageProvider storageProvider(AppProperties properties, DownloadTokenStore tokenStore) {
        AppProperties.Storage storage = properties.storage();
        return switch (storage.provider()) {
            case "supabase" -> new SupabaseStorageProvider(
                    storage.s3Endpoint(), storage.s3Region(), storage.s3AccessKeyId(),
                    storage.s3SecretAccessKey(), storage.bucket());
            case "local" -> new LocalDiskStorageProvider(storage.localRoot(), tokenStore);
            default -> throw new IllegalStateException(
                    "Unknown STORAGE_PROVIDER '" + storage.provider() + "' (expected supabase|local)");
        };
    }
}
