package com.assetshield.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.notification.TestProps;
import com.assetshield.notification.TestTokens;
import com.assetshield.notification.domain.NotificationType;
import com.assetshield.notification.repo.AppNotificationRepository;
import com.assetshield.notification.repo.TipRepository;
import com.assetshield.notification.service.SchedulerService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class SchedulerIT extends NotificationITBase {

    @Autowired
    SchedulerService schedulerService;

    @Autowired
    TipRepository tipRepository;

    @Autowired
    AppNotificationRepository notificationRepository;

    private UUID seedUndeliveredTips(UUID userId) {
        UUID propertyId = UUID.randomUUID();
        when(propertyClient.tipsContext(eq(propertyId))).thenReturn(Optional.of(
                context(propertyId, userId, "COMMERCIAL", KANTAMANTO_LAT, KANTAMANTO_LNG,
                        line("CLOTHING_STOCK", 10, "5000.00"))));
        mockMvc(propertyId, userId);
        return propertyId;
    }

    private void mockMvc(UUID propertyId, UUID userId) {
        try {
            mockMvcRequest(propertyId, userId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void mockMvcRequest(UUID propertyId, UUID userId) throws Exception {
        mockMvc.perform(post("/internal/events/asset-captured")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"propertyId\":\"" + propertyId + "\"}"))
                .andExpect(status().isAccepted());
    }

    private void setFrequency(UUID userId, String frequency) throws Exception {
        mockMvc.perform(put("/api/v1/users/me/notification-preferences")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(userId, "+233200000001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipsFrequency\":\"" + frequency + "\"}"))
                .andExpect(status().isOk());
    }

    private long undeliveredCount(UUID userId) {
        return tipRepository.findByUserIdAndDeliveredAtIsNull(userId).size();
    }

    private long tipNotificationCount(UUID userId) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 100))
                .stream().filter(n -> n.getType() == NotificationType.TIP).count();
    }

    @Test
    void assetCapturedEventsAreDebouncedPerProperty() {
        clock.setDate(2026, 1, 15);
        UUID userId = UUID.randomUUID();
        UUID propertyId = seedUndeliveredTips(userId); // first event → generation

        long afterFirst = tipRepository.propertyIdsForUser(userId).size();
        assertThat(afterFirst).isEqualTo(1);
        verify(propertyClient, times(1)).tipsContext(eq(propertyId));

        clock.advance(Duration.ofMinutes(1)); // inside the 60-minute window
        mockMvc(propertyId, userId);
        verify(propertyClient, times(1)).tipsContext(eq(propertyId)); // still one run

        clock.advance(Duration.ofMinutes(60)); // window elapsed
        mockMvc(propertyId, userId);
        verify(propertyClient, times(2)).tipsContext(eq(propertyId));
    }

    @Test
    void dailyUsersGetDeliveredAnyDayWeeklyOnlyMondayOffAccumulates() throws Exception {
        clock.setDate(2026, 1, 13); // Tuesday (HARMATTAN)
        UUID daily = UUID.randomUUID();
        UUID weekly = UUID.randomUUID();
        UUID off = UUID.randomUUID();
        seedUndeliveredTips(daily);
        seedUndeliveredTips(weekly);
        seedUndeliveredTips(off);
        setFrequency(daily, "DAILY");
        setFrequency(weekly, "WEEKLY");
        setFrequency(off, "OFF");

        schedulerService.deliverTips(); // Tuesday sweep

        assertThat(undeliveredCount(daily)).isZero();
        assertThat(tipNotificationCount(daily)).isEqualTo(1);
        assertThat(undeliveredCount(weekly)).isPositive(); // waits for Monday
        assertThat(tipNotificationCount(weekly)).isZero();
        assertThat(undeliveredCount(off)).isPositive(); // accumulates silently
        assertThat(tipNotificationCount(off)).isZero();

        clock.setDate(2026, 1, 19); // Monday
        schedulerService.deliverTips();

        assertThat(undeliveredCount(weekly)).isZero();
        assertThat(tipNotificationCount(weekly)).isEqualTo(1);
        assertThat(undeliveredCount(off)).isPositive(); // OFF still untouched
        assertThat(tipNotificationCount(off)).isZero();
    }

    @Test
    void redocReminderFiresOncePerSuppressionWindow() {
        clock.setInstant(java.time.Instant.now());
        UUID ownerId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        var stale = new com.assetshield.notification.client.PropertyClient.StaleProperty(
                propertyId, ownerId, "Osu Kiosk", "2025-10-01T00:00:00Z");
        when(propertyClient.staleDocumentation(anyInt(), anyInt(), anyInt()))
                .thenReturn(new com.assetshield.notification.client.PropertyClient.StalePage(
                        List.of(stale), 0, 100, 1, 1));

        schedulerService.remindStaleDocumentation();
        schedulerService.remindStaleDocumentation(); // second sweep: suppressed

        long reminders = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(ownerId, PageRequest.of(0, 50))
                .stream().filter(n -> n.getType() == NotificationType.REDOC_REMINDER).count();
        assertThat(reminders).isEqualTo(1);

        // window elapses → exactly one more
        clock.advance(Duration.ofDays(91));
        schedulerService.remindStaleDocumentation();
        long after = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(ownerId, PageRequest.of(0, 50))
                .stream().filter(n -> n.getType() == NotificationType.REDOC_REMINDER).count();
        assertThat(after).isEqualTo(2);
    }
}
