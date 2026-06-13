package com.assetshield.notification.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FCM_MODE=firebase: multicast to every active device. Tokens FCM reports as
 * UNREGISTERED/INVALID_ARGUMENT are returned as invalid so the dispatch
 * pipeline can revoke them; any other per-token error counts as a failure.
 */
public class FirebasePushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushSender.class);

    private final FirebaseMessaging messaging;

    public FirebasePushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public PushOutcome send(List<String> tokens, String title, String body, Map<String, String> data) {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build();
        BatchResponse batch;
        try {
            batch = messaging.sendEachForMulticast(message);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM multicast failed outright: {}", e.getMessage());
            return new PushOutcome(0, tokens.size(), List.of());
        }

        int success = 0;
        int failure = 0;
        List<String> invalid = new ArrayList<>();
        List<SendResponse> responses = batch.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse response = responses.get(i);
            if (response.isSuccessful()) {
                success++;
                continue;
            }
            MessagingErrorCode code = response.getException() == null
                    ? null : response.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                invalid.add(tokens.get(i)); // dead token — caller revokes it
            } else {
                failure++;
                log.warn("FCM send failed for one token: {}", code);
            }
        }
        return new PushOutcome(success, failure, invalid);
    }
}
