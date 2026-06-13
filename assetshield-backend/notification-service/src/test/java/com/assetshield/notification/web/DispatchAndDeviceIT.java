package com.assetshield.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.notification.TestProps;
import com.assetshield.notification.TestTokens;
import com.assetshield.notification.domain.AppNotification;
import com.assetshield.notification.domain.NotificationStatus;
import com.assetshield.notification.domain.NotificationType;
import com.assetshield.notification.push.PushSender;
import com.assetshield.notification.repo.DeviceTokenRepository;
import com.assetshield.notification.service.NotificationDispatchService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class DispatchAndDeviceIT extends NotificationITBase {

    @Autowired
    NotificationDispatchService dispatchService;

    @Autowired
    DeviceTokenRepository deviceTokenRepository;

    private void registerToken(UUID userId, String token) throws Exception {
        mockMvc.perform(put("/api/v1/users/me/device-token")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(userId, "+233200000001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"" + token + "\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void noDevicesMeansHistoryOnlyDeliveryMarkedSent() {
        UUID userId = UUID.randomUUID();
        AppNotification result = dispatchService.dispatch(userId, NotificationType.DOSSIER_READY,
                "Your dossier is ready", "Download it from the app.", Map.of("dossierId", "x"));
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getSentAt()).isNotNull();
    }

    @Test
    void activeDevicesReceiveThePushAndRevokedOnesDoNot() throws Exception {
        UUID userId = UUID.randomUUID();
        String keep = "tok-keep-" + UUID.randomUUID();
        String drop = "tok-drop-" + UUID.randomUUID();
        registerToken(userId, keep);
        registerToken(userId, drop);
        mockMvc.perform(delete("/api/v1/users/me/device-token")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(userId, "+233200000001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"" + drop + "\"}"))
                .andExpect(status().isOk());

        AppNotification result = dispatchService.dispatch(userId, NotificationType.QUOTE_ISSUED,
                "Quote received", "An agent sent a quote.", Map.of());
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
        verify(pushSender).send(tokens.capture(), anyString(), anyString(), anyMap());
        assertThat(tokens.getValue()).containsExactly(keep);
    }

    @Test
    void tokenReRegisteredByAnotherUserMovesToThem() throws Exception {
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        String shared = "tok-shared-" + UUID.randomUUID();
        registerToken(firstOwner, shared);
        mockMvc.perform(put("/api/v1/users/me/device-token")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secondOwner, "+233200000002"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"" + shared + "\",\"platform\":\"IOS\"}"))
                .andExpect(status().isOk());

        var live = deviceTokenRepository.findByFcmTokenAndRevokedAtIsNull(shared).orElseThrow();
        assertThat(live.getUserId()).isEqualTo(secondOwner);
        assertThat(deviceTokenRepository.findByUserIdAndRevokedAtIsNull(firstOwner)).isEmpty();
    }

    @Test
    void tokensReportedDeadByFcmAreAutoRevoked() throws Exception {
        UUID userId = UUID.randomUUID();
        String dead = "tok-dead-" + UUID.randomUUID();
        registerToken(userId, dead);
        doReturn(new PushSender.PushOutcome(0, 0, List.of(dead)))
                .when(pushSender).send(anyList(), anyString(), anyString(), anyMap());

        dispatchService.dispatch(userId, NotificationType.AGENT_INTEREST,
                "Interest", "An agent is interested.", Map.of());

        assertThat(deviceTokenRepository.findByFcmTokenAndRevokedAtIsNull(dead)).isEmpty();
    }

    @Test
    void internalSendEndpointAcceptsAndEventuallyWritesHistory() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(post("/internal/notifications/send")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","type":"HOUSEHOLD_INVITE","title":"You are invited",
                                 "body":"Ama invited you to a household.","payload":{"invitationId":"abc"}}
                                """.formatted(userId)))
                .andExpect(status().isAccepted());

        // dispatch is async (pool of 2) — poll briefly for the history row
        boolean written = false;
        for (int i = 0; i < 50 && !written; i++) {
            Thread.sleep(100);
            var response = mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .get("/api/v1/users/me/notifications")
                                    .header(HttpHeaders.AUTHORIZATION,
                                            TestTokens.bearer(userId, "+233200000001")))
                    .andReturn().getResponse().getContentAsString();
            written = response.contains("HOUSEHOLD_INVITE");
        }
        assertThat(written).as("async dispatch wrote the history row").isTrue();
    }

    @Test
    void unknownNotificationTypeIsRejectedNotPersisted() throws Exception {
        mockMvc.perform(post("/internal/notifications/send")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","type":"NOT_A_TYPE","title":"x","body":"y"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }
}
