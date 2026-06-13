package com.assetshield.property.storage;

import com.assetshield.property.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects the storage backend by env STORAGE_PROVIDER=supabase|firebase|local. */
@Configuration
public class StorageConfig {

    @Bean
    public StorageProvider storageProvider(AppProperties properties, DownloadTokenStore tokenStore) {
        AppProperties.Storage storage = properties.storage();
        return switch (storage.provider()) {
            case "supabase" -> new SupabaseStorageProvider(
                    storage.supabaseUrl(), storage.supabaseServiceKey(), storage.supabaseBucket());
            case "firebase" -> new FirebaseStorageProvider(
                    storage.firebaseServiceAccountPath(), storage.firebaseBucket());
            case "local" -> new LocalDiskStorageProvider(storage.localRoot(), tokenStore);
            default -> throw new IllegalStateException(
                    "Unknown STORAGE_PROVIDER '" + storage.provider()
                            + "' (expected supabase|firebase|local)");
        };
    }
}
