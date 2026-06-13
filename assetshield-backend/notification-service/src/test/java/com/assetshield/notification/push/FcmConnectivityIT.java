package com.assetshield.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import java.io.FileInputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Proves the real Firebase service account authenticates to FCM, guarded by
 * FCM_TEST_ACCOUNT (host path to the service-account JSON) so it only runs on
 * demand and skips cleanly in CI.
 *
 * Without a device this can't land a push on a phone, so it does a dry-run
 * send to a deliberately-invalid token: reaching a messaging-level error
 * (INVALID_ARGUMENT etc.) proves the credentials were accepted and FCM was
 * reached — a bad service account would fail earlier with an auth error.
 * If FCM_TEST_TOKEN is also set, it does a real dry-run send to that token
 * and asserts success.
 */
class FcmConnectivityIT {

    private static final Set<MessagingErrorCode> POST_AUTH_TOKEN_ERRORS = Set.of(
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.SENDER_ID_MISMATCH);

    private static FirebaseMessaging messaging() throws Exception {
        String path = System.getenv("FCM_TEST_ACCOUNT");
        String appName = "fcm-connectivity-it";
        FirebaseApp app = FirebaseApp.getApps().stream()
                .filter(a -> a.getName().equals(appName))
                .findFirst()
                .orElseGet(() -> {
                    try (FileInputStream in = new FileInputStream(path)) {
                        return FirebaseApp.initializeApp(FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(in))
                                .build(), appName); // FCM only — no storage bucket
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
        return FirebaseMessaging.getInstance(app);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FCM_TEST_ACCOUNT", matches = ".+")
    void serviceAccountAuthenticatesToFcm() throws Exception {
        FirebaseMessaging messaging = messaging();
        Message message = Message.builder()
                .setToken("smoke-test-deliberately-invalid-token")
                .setNotification(Notification.builder()
                        .setTitle("AssetShield FCM connectivity check")
                        .setBody("dry run").build())
                .setAndroidConfig(AndroidConfig.builder().build())
                .build();
        try {
            messaging.send(message, true); // validate_only — no push is delivered
            // a clearly-invalid token succeeding is unexpected but still proves auth
        } catch (FirebaseMessagingException e) {
            assertThat(e.getMessagingErrorCode())
                    .as("a messaging-level error proves the credentials authenticated; "
                            + "an auth/credential failure would surface differently")
                    .isIn(POST_AUTH_TOKEN_ERRORS);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FCM_TEST_TOKEN", matches = ".+")
    void realTokenDryRunSucceeds() throws Exception {
        String messageId = messaging().send(Message.builder()
                .setToken(System.getenv("FCM_TEST_TOKEN"))
                .setNotification(Notification.builder()
                        .setTitle("AssetShield FCM test")
                        .setBody("dry-run validation").build())
                .build(), true); // dry run: validates against the real token, sends nothing
        assertThat(messageId).isNotBlank();
    }
}
