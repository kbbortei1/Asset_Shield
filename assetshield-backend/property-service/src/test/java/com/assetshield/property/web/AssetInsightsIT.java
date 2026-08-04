package com.assetshield.property.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.property.TestProps;
import com.assetshield.property.TestTokens;
import com.assetshield.property.client.AuthUserClient;
import com.assetshield.property.service.Sha256;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Search, CSV export, analytics, timeline, maintenance feed, duplicate warning. */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "STORAGE_PROVIDER=local",
        "STORAGE_LOCAL_ROOT=target/it-storage-insights",
        "TIER_LOOKUP_MODE=stub",
        "STUB_TIER=PRO",
        "NOTIFICATIONS_MODE=log",
        "AUTH_SERVICE_URI=http://auth-service.invalid"
})
@AutoConfigureMockMvc
@Testcontainers
class AssetInsightsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AuthUserClient authUserClient;

    final UUID ownerId = UUID.randomUUID();
    final UUID memberId = UUID.randomUUID();
    final String ownerPhone = "+233244100001";
    final String memberPhone = "+233244100002";
    final String ownerBearer = TestTokens.bearer(ownerId, ownerPhone);
    final String memberBearer = TestTokens.bearer(memberId, memberPhone);

    @BeforeEach
    void stubAuthLookups() {
        when(authUserClient.byPhone(any())).thenReturn(Optional.empty());
        when(authUserClient.byPhone(memberPhone)).thenReturn(Optional.of(
                new AuthUserClient.AuthUserInfo(memberId, "Ama Mensah", memberPhone, "OWNER", "ACTIVE")));
        when(authUserClient.byId(ownerId)).thenReturn(Optional.of(
                new AuthUserClient.AuthUserInfo(ownerId, "Kwesi Boateng", ownerPhone, "OWNER", "ACTIVE")));
        when(authUserClient.byId(memberId)).thenReturn(Optional.of(
                new AuthUserClient.AuthUserInfo(memberId, "Ama Mensah", memberPhone, "OWNER", "ACTIVE")));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID createProperty(String bearer, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/properties")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","type":"RESIDENTIAL",
                                 "gpsLat":5.6037,"gpsLng":-0.1870,"locality":"Adabraka"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asString());
    }

    private static String metadata(String hash, String description, String category, String value,
                                   LocalDate warrantyExpiresOn, LocalDate nextServiceOn) {
        return """
                {"description":%s,"estimatedValue":%s,"category":"%s",
                 "warrantyExpiresOn":%s,"nextServiceOn":%s,
                 "photos":[{"sha256Hash":"%s","gpsLat":5.6037,"gpsLng":-0.1870,
                            "capturedAt":"2026-06-10T10:00:00Z"}]}
                """.formatted(quote(description), value, category,
                warrantyExpiresOn == null ? "null" : "\"" + warrantyExpiresOn + "\"",
                nextServiceOn == null ? "null" : "\"" + nextServiceOn + "\"", hash);
    }

    private static String quote(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private MvcResult upload(String bearer, UUID propertyId, String seed, String description,
                             String category, String value, LocalDate warranty, LocalDate service)
            throws Exception {
        byte[] bytes = ("fake-jpeg-" + seed).getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(multipart("/api/v1/properties/{id}/assets", propertyId)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes))
                        .file(new MockMultipartFile("metadata", "metadata", "application/json",
                                metadata(Sha256.hex(bytes), description, category, value,
                                        warranty, service).getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn();
    }

    private UUID assetIdOf(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asString());
    }

    // ── search ───────────────────────────────────────────────────────────────

    @Test
    void searchFiltersByTextCategoryAndValueRange() throws Exception {
        UUID propertyId = createProperty(ownerBearer, "Search House");
        upload(ownerBearer, propertyId, "tv", "Samsung Smart TV", "ELECTRONICS", "3500.00", null, null);
        upload(ownerBearer, propertyId, "chair", "Office chair", "FURNITURE", "150.00", null, null);
        upload(ownerBearer, propertyId, "gen", "Diesel generator", "MACHINERY", "2500.00", null, null);

        mockMvc.perform(get("/api/v1/properties/{id}/assets", propertyId)
                        .param("q", "tv")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].description").value("Samsung Smart TV"));

        mockMvc.perform(get("/api/v1/properties/{id}/assets", propertyId)
                        .param("minValue", "1000")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/properties/{id}/assets", propertyId)
                        .param("q", "generator").param("category", "MACHINERY")
                        .param("minValue", "1000").param("maxValue", "3000")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].description").value("Diesel generator"));

        mockMvc.perform(get("/api/v1/properties/{id}/assets", propertyId)
                        .param("q", "no-such-asset")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── fraud: cross-property duplicate warning ──────────────────────────────

    @Test
    void samePhotoOnAnotherPropertyWarnsButSucceeds() throws Exception {
        UUID first = createProperty(ownerBearer, "First House");
        UUID second = createProperty(ownerBearer, "Second House");
        byte[] bytes = "fake-jpeg-shared".getBytes(StandardCharsets.UTF_8);
        String meta = metadata(Sha256.hex(bytes), "Shared TV", "ELECTRONICS", "1000.00", null, null);

        mockMvc.perform(multipart("/api/v1/properties/{id}/assets", first)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes))
                        .file(new MockMultipartFile("metadata", "metadata", "application/json",
                                meta.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.duplicateWarning").value(false));

        mockMvc.perform(multipart("/api/v1/properties/{id}/assets", second)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes))
                        .file(new MockMultipartFile("metadata", "metadata", "application/json",
                                meta.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.duplicateWarning").value(true));
    }

    // ── CSV export ───────────────────────────────────────────────────────────

    @Test
    void csvExportQuotesEscapesAndEnforcesPermission() throws Exception {
        UUID propertyId = createProperty(ownerBearer, "Export House");
        upload(ownerBearer, propertyId, "formula", "=SUM(A1), \"quoted\"", "OTHER", "10.00",
                null, null);

        MvcResult result = mockMvc.perform(get("/api/v1/properties/{id}/assets/export", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"assetshield-Export-House.csv\""))
                .andReturn();
        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Asset ID,Description,Category");
        // formula neutralized with a leading apostrophe, quotes doubled
        assertThat(csv).contains("\"'=SUM(A1), \"\"quoted\"\"\"");

        // a plain member may view but not export
        inviteAndAccept(propertyId, false);
        mockMvc.perform(get("/api/v1/properties/{id}/assets/export", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("FORBIDDEN"));

        // a stranger is not even a member
        mockMvc.perform(get("/api/v1/properties/{id}/assets/export", propertyId)
                        .header(HttpHeaders.AUTHORIZATION,
                                TestTokens.bearer(UUID.randomUUID(), "+233244999998")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("NOT_MEMBER"));
    }

    private void inviteAndAccept(UUID propertyId, boolean canExport) throws Exception {
        mockMvc.perform(post("/api/v1/properties/{id}/invite", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteePhone\":\"" + memberPhone + "\",\"canExport\":"
                                + canExport + "}"))
                .andExpect(status().isCreated());
        MvcResult invitations = mockMvc.perform(get("/api/v1/users/me/invitations")
                        .header(HttpHeaders.AUTHORIZATION, memberBearer))
                .andExpect(status().isOk())
                .andReturn();
        UUID invitationId = UUID.fromString(objectMapper
                .readTree(invitations.getResponse().getContentAsString())
                .get("data").get("items").get(0).get("id").asString());
        mockMvc.perform(put("/api/v1/invitations/{id}/respond", invitationId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isOk());
    }

    // ── analytics ────────────────────────────────────────────────────────────

    @Test
    void analyticsRollsUpAcrossProperties() throws Exception {
        UUID first = createProperty(ownerBearer, "Analytics One");
        UUID second = createProperty(ownerBearer, "Analytics Two");
        upload(ownerBearer, first, "a1", "TV", "ELECTRONICS", "3000.00", null, null);
        upload(ownerBearer, first, "a2", "Chair", "FURNITURE", "500.00", null, null);
        upload(ownerBearer, second, "a3", "Laptop", "ELECTRONICS", "7000.00", null, null);

        mockMvc.perform(get("/api/v1/users/me/asset-analytics")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.propertyCount").value(2))
                .andExpect(jsonPath("$.data.assetCount").value(3))
                .andExpect(jsonPath("$.data.totalValue").value(10500.00))
                .andExpect(jsonPath("$.data.byCategory[0].category").value("ELECTRONICS"))
                .andExpect(jsonPath("$.data.byCategory[0].count").value(2))
                .andExpect(jsonPath("$.data.byCategory[0].value").value(10000.00))
                // sorted by value desc: Analytics Two (7000) first
                .andExpect(jsonPath("$.data.byProperty[0].name").value("Analytics Two"))
                .andExpect(jsonPath("$.data.byProperty[0].totalValue").value(7000.00));

        // a fresh user sees an empty rollup, not an error
        mockMvc.perform(get("/api/v1/users/me/asset-analytics")
                        .header(HttpHeaders.AUTHORIZATION,
                                TestTokens.bearer(UUID.randomUUID(), "+233244999997")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.propertyCount").value(0))
                .andExpect(jsonPath("$.data.totalValue").value(0));
    }

    // ── timeline ─────────────────────────────────────────────────────────────

    @Test
    void timelineDerivesEventsNewestFirst() throws Exception {
        UUID propertyId = createProperty(ownerBearer, "Timeline House");
        UUID assetId = assetIdOf(upload(ownerBearer, propertyId, "t1", "Fridge",
                "ELECTRONICS", "1200.00", null, null));

        byte[] receipt = "fake-receipt-t1".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/v1/assets/{id}/receipts", assetId)
                        .file(new MockMultipartFile("file", "r.jpg", "image/jpeg", receipt))
                        .file(new MockMultipartFile("metadata", "metadata", "application/json",
                                ("{\"sha256Hash\":\"" + Sha256.hex(receipt) + "\"}")
                                        .getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/assets/{id}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/properties/{id}/timeline", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("items");
        assertThat(items).hasSize(4);
        assertThat(items.get(0).get("type").asString()).isEqualTo("ASSET_REMOVED");
        assertThat(items.get(0).get("label").asString()).isEqualTo("Fridge");
        assertThat(items.get(1).get("type").asString()).isEqualTo("RECEIPT_ADDED");
        assertThat(items.get(2).get("type").asString()).isEqualTo("ASSET_ADDED");
        assertThat(items.get(3).get("type").asString()).isEqualTo("PROPERTY_CREATED");
        assertThat(items.get(3).get("label").asString()).isEqualTo("Timeline House");

        // members can read it, strangers cannot
        mockMvc.perform(get("/api/v1/properties/{id}/timeline", propertyId)
                        .header(HttpHeaders.AUTHORIZATION,
                                TestTokens.bearer(UUID.randomUUID(), "+233244999996")))
                .andExpect(status().isForbidden());
    }

    // ── maintenance feed ─────────────────────────────────────────────────────

    @Test
    void maintenanceFeedServesWarrantyAndServiceKinds() throws Exception {
        UUID propertyId = createProperty(ownerBearer, "Maintenance House");
        LocalDate warranty = LocalDate.now().plusDays(7);
        UUID assetId = assetIdOf(upload(ownerBearer, propertyId, "m1", "Washing machine",
                "ELECTRONICS", "2000.00", warranty, null));

        mockMvc.perform(get("/internal/assets/maintenance-due")
                        .param("kind", "WARRANTY").param("withinDays", "14")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.data.items[0].propertyName").value("Maintenance House"))
                .andExpect(jsonPath("$.data.items[0].ownerUserId").value(ownerId.toString()))
                .andExpect(jsonPath("$.data.items[0].kind").value("WARRANTY"))
                .andExpect(jsonPath("$.data.items[0].dueOn").value(warranty.toString()));

        // nothing due for servicing yet
        mockMvc.perform(get("/internal/assets/maintenance-due")
                        .param("kind", "SERVICE")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // the owner schedules servicing via the normal edit endpoint
        LocalDate service = LocalDate.now().plusDays(3);
        mockMvc.perform(put("/api/v1/assets/{id}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nextServiceOn\":\"" + service + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextServiceOn").value(service.toString()));

        mockMvc.perform(get("/internal/assets/maintenance-due")
                        .param("kind", "SERVICE")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("SERVICE"));

        // outside the window → not returned
        mockMvc.perform(get("/internal/assets/maintenance-due")
                        .param("kind", "WARRANTY").param("withinDays", "3")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // bad kind → 400, missing key → 401
        mockMvc.perform(get("/internal/assets/maintenance-due")
                        .param("kind", "BOGUS")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/internal/assets/maintenance-due").param("kind", "WARRANTY"))
                .andExpect(status().isUnauthorized());
    }
}
