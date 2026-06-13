package com.assetshield.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Per-token FCM error handling: dead tokens out, transient errors counted. */
class FirebasePushSenderTest {

    private static SendResponse success() {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    private static SendResponse failure(MessagingErrorCode code) {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(false);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        when(response.getException()).thenReturn(exception);
        return response;
    }

    @Test
    void unregisteredTokensAreReportedInvalidOthersCountAsFailures() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        BatchResponse batch = mock(BatchResponse.class);
        // build the responses BEFORE stubbing — nested when() inside
        // thenReturn(...) trips Mockito's unfinished-stubbing detection
        List<SendResponse> responses = List.of(
                success(),
                failure(MessagingErrorCode.UNREGISTERED),
                failure(MessagingErrorCode.INTERNAL),
                failure(MessagingErrorCode.INVALID_ARGUMENT));
        when(batch.getResponses()).thenReturn(responses);
        when(messaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batch);

        FirebasePushSender sender = new FirebasePushSender(messaging);
        PushSender.PushOutcome outcome = sender.send(List.of("t1", "t2", "t3", "t4"),
                "Title", "Body", Map.of("type", "TIP"));

        assertThat(outcome.successCount()).isEqualTo(1);
        assertThat(outcome.failureCount()).isEqualTo(1); // INTERNAL only
        // dead tokens — the dispatch pipeline revokes exactly these
        assertThat(outcome.invalidTokens()).containsExactly("t2", "t4");
    }

    @Test
    void outrightMulticastFailureFailsEveryToken() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenThrow(mock(FirebaseMessagingException.class));

        PushSender.PushOutcome outcome = new FirebasePushSender(messaging)
                .send(List.of("t1", "t2"), "Title", "Body", Map.of());

        assertThat(outcome.successCount()).isZero();
        assertThat(outcome.failureCount()).isEqualTo(2);
        assertThat(outcome.invalidTokens()).isEmpty();
    }
}
