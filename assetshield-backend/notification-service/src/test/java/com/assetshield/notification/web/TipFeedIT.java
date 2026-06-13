package com.assetshield.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.notification.TestTokens;
import com.assetshield.notification.domain.Tip;
import com.assetshield.notification.repo.TipRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class TipFeedIT extends NotificationITBase {

    @Autowired
    TipRepository tipRepository;

    private Tip seedTip(UUID userId, UUID propertyId, String text) {
        Tip tip = new Tip();
        tip.setUserId(userId);
        tip.setPropertyId(propertyId);
        // a synthetic template id satisfies the FK only via a real template —
        // reuse any seeded one
        tip.setTipTemplateId(anyTemplateId());
        tip.setTipText(text);
        tip.setCategory("GENERAL");
        return tipRepository.saveAndFlush(tip);
    }

    @Autowired
    com.assetshield.notification.repo.TipTemplateRepository templateRepository;

    private static final java.util.concurrent.atomic.AtomicInteger TEMPLATE_CURSOR =
            new java.util.concurrent.atomic.AtomicInteger();

    /** Walks the 50 seeded templates so ux_tip_once never collides. */
    private UUID anyTemplateId() {
        var all = templateRepository.findAll();
        return all.get(TEMPLATE_CURSOR.getAndIncrement() % all.size()).getId();
    }

    @Test
    void feedIsNewestFirstAndReadIsIdempotent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        Tip older = seedTip(userId, propertyId, "older tip");
        Thread.sleep(10); // distinct created_at for a deterministic order
        Tip newer = seedTip(userId, propertyId, "newer tip");
        String bearer = TestTokens.bearer(userId, "+233200000001");

        mockMvc.perform(get("/api/v1/tips/feed")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$.data.items[1].id").value(older.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].readAt").isEmpty());

        MvcResult first = mockMvc.perform(put("/api/v1/tips/{id}/read", newer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn();
        String firstReadAt = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("data").get("readAt").asString();

        // second read keeps the original timestamp (idempotent) — compare at
        // microsecond precision: PostgreSQL truncates the JVM's nanoseconds
        MvcResult second = mockMvc.perform(put("/api/v1/tips/{id}/read", newer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn();
        String secondReadAt = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("data").get("readAt").asString();
        assertThat(java.time.Instant.parse(secondReadAt))
                .isEqualTo(java.time.Instant.parse(firstReadAt)
                        .truncatedTo(java.time.temporal.ChronoUnit.MICROS));

        // a stranger cannot read-mark someone else's tip — 404, not 403
        mockMvc.perform(put("/api/v1/tips/{id}/read", newer.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                TestTokens.bearer(UUID.randomUUID(), "+233200000009")))
                .andExpect(status().isNotFound());
    }

    @Test
    void propertyTipsRequirePropertyAccess() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        seedTip(ownerId, propertyId, "property tip");

        when(propertyClient.access(eq(propertyId), eq(memberId))).thenReturn("MEMBER");
        when(propertyClient.access(eq(propertyId), eq(strangerId))).thenReturn("NONE");
        when(propertyClient.tipsContext(eq(propertyId))).thenReturn(Optional.of(
                context(propertyId, ownerId, "RESIDENTIAL", KANTAMANTO_LAT, KANTAMANTO_LNG)));

        mockMvc.perform(get("/api/v1/properties/{id}/tips", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(memberId, "+233200000002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].tipText").value("property tip"));

        // existence stays private for outsiders
        mockMvc.perform(get("/api/v1/properties/{id}/tips", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(strangerId, "+233200000003")))
                .andExpect(status().isNotFound());
    }
}
