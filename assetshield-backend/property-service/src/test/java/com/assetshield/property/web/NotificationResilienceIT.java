package com.assetshield.property.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.property.TestProps;
import com.assetshield.property.TestTokens;
import com.assetshield.property.client.AuthUserClient;
import com.assetshield.property.service.Sha256;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Day 6 resilience flip: with NOTIFICATIONS_MODE/EVENTS_MODE=remote and a
 * DEAD notification-service, asset upload still succeeds — the remote
 * clients swallow transport failures (WARN) and never break the business
 * operation.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "STORAGE_PROVIDER=local",
        "STORAGE_LOCAL_ROOT=target/it-storage-resilience",
        "TIER_LOOKUP_MODE=stub",
        "STUB_TIER=PRO",
        "NOTIFICATIONS_MODE=remote",
        "EVENTS_MODE=remote",
        // nothing listens here — every notify/event call fails at transport
        "NOTIFICATION_SERVICE_URL=http://localhost:1",
        "AUTH_SERVICE_URI=http://auth-service.invalid"
})
@AutoConfigureMockMvc
@Testcontainers
class NotificationResilienceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AuthUserClient authUserClient;

    @Test
    void assetUploadSucceedsWithNotificationServiceDown() throws Exception {
        String bearer = TestTokens.bearer(UUID.randomUUID(), "+233244700001");
        MvcResult created = mockMvc.perform(post("/api/v1/properties")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Resilience Shop","type":"COMMERCIAL",
                                 "gpsLat":5.5461,"gpsLng":-0.2117,"locality":"Kantamanto"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID propertyId = UUID.fromString(objectMapper
                .readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asString());

        // the upload fires the remote assetCaptured event at the dead URL —
        // and must still return 201
        byte[] bytes = "resilience-photo".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/v1/properties/{id}/assets", propertyId)
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", bytes))
                        .file(new MockMultipartFile("metadata", "metadata", "application/json",
                                """
                                {"description":"Stock shelf","estimatedValue":150,"category":"CLOTHING_STOCK",
                                 "photos":[{"sha256Hash":"%s","gpsLat":5.5461,"gpsLng":-0.2117,
                                            "capturedAt":"2026-06-12T10:00:00Z"}]}
                                """.formatted(Sha256.hex(bytes)).getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }
}
