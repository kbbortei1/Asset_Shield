package com.assetshield.notification.push;

import com.assetshield.notification.config.AppProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** FCM_MODE-selected PushSender. Fails fast when firebase mode is misconfigured. */
@Configuration
public class PushConfig {

    @Bean
    public PushSender pushSender(AppProperties properties) {
        return switch (properties.fcm().mode()) {
            case "log" -> new LogPushSender();
            case "firebase" -> new FirebasePushSender(FirebaseMessaging.getInstance(firebaseApp(properties)));
            default -> throw new IllegalStateException(
                    "Unknown FCM_MODE '" + properties.fcm().mode() + "' (expected firebase|log)");
        };
    }

    /** Reuses an already-initialized FirebaseApp (storage) when present. */
    private static FirebaseApp firebaseApp(AppProperties properties) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        String path = properties.fcm().firebaseServiceAccountPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "FCM_MODE=firebase requires FIREBASE_SERVICE_ACCOUNT_PATH");
        }
        try (FileInputStream credentials = new FileInputStream(path)) {
            return FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read Firebase service account at " + path, e);
        }
    }
}
