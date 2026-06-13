package com.assetshield.property.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.property.TestProps;
import com.assetshield.property.TestTokens;
import com.assetshield.property.client.AuthUserClient;
import com.assetshield.property.domain.Asset;
import com.assetshield.property.domain.AssetCategory;
import com.assetshield.property.repo.AssetRepository;
import com.assetshield.property.service.Sha256;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

/** With STUB_TIER=FREE the 2nd property and the 31st photo are blocked. */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "STORAGE_PROVIDER=local",
        "STORAGE_LOCAL_ROOT=target/it-storage-free",
        "TIER_LOOKUP_MODE=stub",
        "STUB_TIER=FREE",
        "NOTIFICATIONS_MODE=log",
        "AUTH_SERVICE_URI=http://auth-service.invalid"
})
@AutoConfigureMockMvc
@Testcontainers
class FreeTierLimitIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AssetRepository assetRepository;

    @MockitoBean
    AuthUserClient authUserClient;

    private UUID createProperty(String bearer, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/properties")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Kiosk","type":"COMMERCIAL",
                                 "gpsLat":5.6037,"gpsLng":-0.1870,"locality":"Osu"}
                                """))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        if (expectedStatus != 201) {
            return null;
        }
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asString());
    }

    @Test
    void secondPropertyIsBlockedOnFree() throws Exception {
        String bearer = TestTokens.bearer(UUID.randomUUID(), "+233244100001");
        createProperty(bearer, 201);

        mockMvc.perform(post("/api/v1/properties")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Second","type":"RENTAL",
                                 "gpsLat":5.6,"gpsLng":-0.18,"locality":"Tema"}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.errorCode").value("FREE_TIER_LIMIT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void thirtyFirstPhotoIsBlockedOnFree() throws Exception {
        UUID userId = UUID.randomUUID();
        String bearer = TestTokens.bearer(userId, "+233244100002");
        UUID propertyId = createProperty(bearer, 201);

        // 30 existing photos (seeded directly; the API path is exercised below)
        for (int i = 0; i < 30; i++) {
            Asset a = new Asset();
            a.setPropertyId(propertyId);
            a.setCreatedByUserId(userId);
            a.setPhotoUrl("assets/" + propertyId + "/seed" + i + ".jpg");
            a.setSha256Hash(Sha256.hex(("seed" + i).getBytes(StandardCharsets.UTF_8)));
            a.setGpsLat(new BigDecimal("5.603700"));
            a.setGpsLng(new BigDecimal("-0.187000"));
            a.setCapturedAt(Instant.now());
            a.setDescription("Seed " + i);
            a.setEstimatedValue(new BigDecimal("10.00"));
            a.setCategory(AssetCategory.OTHER);
            assetRepository.save(a);
        }

        byte[] bytes = "photo-31".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/v1/properties/{id}/assets", propertyId)
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", bytes))
                        .file(new MockMultipartFile("metadata", "metadata", "application/json",
                                """
                                {"sha256Hash":"%s","gpsLat":5.6037,"gpsLng":-0.1870,
                                 "capturedAt":"2026-06-10T10:00:00Z","description":"31st",
                                 "estimatedValue":10,"category":"OTHER"}
                                """.formatted(Sha256.hex(bytes)).getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.data.errorCode").value("FREE_TIER_LIMIT"));
    }
}
