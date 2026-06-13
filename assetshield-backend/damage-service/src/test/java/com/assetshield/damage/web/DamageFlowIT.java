package com.assetshield.damage.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.damage.TestProps;
import com.assetshield.damage.TestTokens;
import com.assetshield.damage.client.AccessLevel;
import com.assetshield.damage.client.PropertyInternalClient;
import com.assetshield.damage.service.Sha256;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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

@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "STORAGE_PROVIDER=local",
        "STORAGE_LOCAL_ROOT=target/it-storage-damage",
        "PAIRING_RADIUS_METERS=25",
        "PROPERTY_SERVICE_URL=http://property-service.invalid"
})
@AutoConfigureMockMvc
@Testcontainers
class DamageFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PropertyInternalClient propertyClient;

    final UUID propertyId = UUID.randomUUID();
    final UUID assetId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final UUID exportMemberId = UUID.randomUUID();
    final UUID memberId = UUID.randomUUID();
    final UUID outsiderId = UUID.randomUUID();
    final String ownerBearer = TestTokens.bearer(ownerId, "+233244300001");
    final String exportBearer = TestTokens.bearer(exportMemberId, "+233244300002");
    final String memberBearer = TestTokens.bearer(memberId, "+233244300003");
    final String outsiderBearer = TestTokens.bearer(outsiderId, "+233244300004");

    // asset sits at the spec's Accra fixture point
    final BigDecimal assetLat = new BigDecimal("5.546111");
    final BigDecimal assetLng = new BigDecimal("-0.211667");

    @BeforeEach
    void stubPropertyService() {
        when(propertyClient.access(any(), any())).thenReturn(AccessLevel.NONE);
        when(propertyClient.access(eq(propertyId), eq(ownerId))).thenReturn(AccessLevel.OWNER);
        when(propertyClient.access(eq(propertyId), eq(exportMemberId))).thenReturn(AccessLevel.MEMBER_EXPORT);
        when(propertyClient.access(eq(propertyId), eq(memberId))).thenReturn(AccessLevel.MEMBER);
        when(propertyClient.property(eq(propertyId))).thenReturn(Optional.of(
                new PropertyInternalClient.PropertyInfo(propertyId, ownerId, "Adabraka Flat",
                        "RESIDENTIAL", "Adabraka", false, false)));
        when(propertyClient.property(any())).thenAnswer(invocation ->
                propertyId.equals(invocation.getArgument(0))
                        ? Optional.of(new PropertyInternalClient.PropertyInfo(propertyId, ownerId,
                        "Adabraka Flat", "RESIDENTIAL", "Adabraka", false, false))
                        : Optional.empty());
        when(propertyClient.asset(eq(assetId))).thenReturn(Optional.of(assetInfo("3500.00", "Samsung TV")));
        when(propertyClient.assetsNear(eq(propertyId), any(), any(), anyDouble())).thenReturn(List.of(
                new PropertyInternalClient.AssetNear(assetId, 12.3, "Samsung TV",
                        new BigDecimal("3500.00"), "ELECTRONICS", "/api/v1/public/files/thumb",
                        "c".repeat(64), Instant.parse("2026-06-01T08:00:00Z"))));
    }

    private PropertyInternalClient.AssetInfo assetInfo(String value, String description) {
        return new PropertyInternalClient.AssetInfo(assetId, propertyId, "assets/" + propertyId + "/x.jpg",
                "c".repeat(64), assetLat, assetLng, Instant.parse("2026-06-01T08:00:00Z"),
                description, new BigDecimal(value), "ELECTRONICS");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID createReport(String bearer) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/properties/{id}/damage-reports", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disasterType":"FIRE","description":"Kitchen fire",
                                 "occurredAt":"2026-06-11T06:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asString());
    }

    private static byte[] photoBytes(String seed) {
        return ("fake-jpeg-" + seed).getBytes(StandardCharsets.UTF_8);
    }

    private MvcResult uploadPhoto(String bearer, UUID reportId, byte[] bytes, String declaredHash)
            throws Exception {
        return mockMvc.perform(multipart("/api/v1/damage-reports/{id}/photos", reportId)
                        .file(new MockMultipartFile("file", "after.jpg", "image/jpeg", bytes))
                        .file(new MockMultipartFile("metadata", "metadata", "application/json",
                                """
                                {"sha256Hash":"%s","gpsLat":5.546120,"gpsLng":-0.211670,
                                 "capturedAt":"2026-06-11T07:00:00Z","description":"burnt corner"}
                                """.formatted(declaredHash).getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn();
    }

    private UUID uploadPhotoOk(String bearer, UUID reportId, String seed) throws Exception {
        byte[] bytes = photoBytes(seed);
        MvcResult result = uploadPhoto(bearer, reportId, bytes, Sha256.hex(bytes));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("photo").get("id").asString());
    }

    private MvcResult createPair(String bearer, UUID reportId, UUID photoId, UUID asset) throws Exception {
        return mockMvc.perform(post("/api/v1/damage-reports/{id}/pairs", reportId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"damagePhotoId":"%s","assetId":"%s","pairingMethod":"GPS_AUTO"}
                                """.formatted(photoId, asset)))
                .andReturn();
    }

    // ── flows ────────────────────────────────────────────────────────────────

    @Test
    void fullFlowUploadSuggestPairFreezeComplete() throws Exception {
        UUID reportId = createReport(ownerBearer);

        // upload → suggestions from mocked assets-near
        byte[] bytes = photoBytes("burnt-tv");
        MvcResult uploaded = uploadPhoto(ownerBearer, reportId, bytes, Sha256.hex(bytes));
        assertThat(uploaded.getResponse().getStatus()).isEqualTo(201);
        JsonNode uploadBody = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("data");
        assertThat(uploadBody.get("photo").get("photoUrl").asString())
                .startsWith("/api/v1/public/damage-files/");
        assertThat(uploadBody.get("pairingSuggestions").get(0).get("assetId").asString())
                .isEqualTo(assetId.toString());
        UUID photoId = UUID.fromString(uploadBody.get("photo").get("id").asString());

        // pair from the suggestion — snapshot frozen, distance from real Haversine
        MvcResult paired = createPair(ownerBearer, reportId, photoId, assetId);
        assertThat(paired.getResponse().getStatus()).isEqualTo(201);
        JsonNode pair = objectMapper.readTree(paired.getResponse().getContentAsString()).get("data");
        assertThat(pair.get("before").get("estimatedValue").decimalValue())
                .isEqualByComparingTo("3500.00");
        double distance = pair.get("distanceMeters").asDouble();
        assertThat(distance).isBetween(0.0, 25.0);

        // duplicate pair → 409
        MvcResult duplicate = createPair(ownerBearer, reportId, photoId, assetId);
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(duplicate.getResponse().getContentAsString())
                .get("data").get("errorCode").asString()).isEqualTo("PAIR_EXISTS");

        // mutate the live asset AFTER pairing — the frozen before block must not move
        when(propertyClient.asset(eq(assetId)))
                .thenReturn(Optional.of(assetInfo("9999.99", "Edited later")));
        mockMvc.perform(get("/api/v1/damage-reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photos[0].paired").value(true))
                .andExpect(jsonPath("$.data.pairs[0].before.estimatedValue").value(3500.00))
                .andExpect(jsonPath("$.data.pairs[0].before.description").value("Samsung TV"));

        // second photo paired with the SAME asset — loss must count the asset once
        UUID photo2 = uploadPhotoOk(ownerBearer, reportId, "burnt-tv-other-angle");
        assertThat(createPair(ownerBearer, reportId, photo2, assetId).getResponse().getStatus())
                .isEqualTo(201);

        mockMvc.perform(put("/api/v1/damage-reports/{id}/complete", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalEstimatedLoss").value(3500.00))
                .andExpect(jsonPath("$.data.photoCount").value(2))
                .andExpect(jsonPath("$.data.pairCount").value(2))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        // everything is immutable after completion
        MvcResult lateUpload = uploadPhoto(ownerBearer, reportId, photoBytes("late"),
                Sha256.hex(photoBytes("late")));
        assertThat(lateUpload.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(lateUpload.getResponse().getContentAsString())
                .get("data").get("errorCode").asString()).isEqualTo("INVALID_STATE_TRANSITION");

        mockMvc.perform(put("/api/v1/damage-reports/{id}/complete", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void tamperedHashStoresNothing() throws Exception {
        UUID reportId = createReport(ownerBearer);
        byte[] bytes = photoBytes("tampered");

        MvcResult result = uploadPhoto(ownerBearer, reportId, bytes,
                Sha256.hex(photoBytes("something-else")));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("error");
        assertThat(body.get("data").get("errorCode").asString()).isEqualTo("HASH_MISMATCH");

        mockMvc.perform(get("/api/v1/damage-reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photos").isEmpty());
        assertThat(Files.notExists(Path.of("target/it-storage-damage/damage/" + reportId))).isTrue();
    }

    @Test
    void duplicatePhotoHashIs409() throws Exception {
        UUID reportId = createReport(ownerBearer);
        byte[] bytes = photoBytes("same-photo");
        String hash = Sha256.hex(bytes);
        assertThat(uploadPhoto(ownerBearer, reportId, bytes, hash).getResponse().getStatus()).isEqualTo(201);

        MvcResult second = uploadPhoto(ownerBearer, reportId, bytes, hash);
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(second.getResponse().getContentAsString())
                .get("data").get("errorCode").asString()).isEqualTo("DUPLICATE_PHOTO_HASH");
    }

    @Test
    void assetFromAnotherPropertyCannotBePaired() throws Exception {
        UUID reportId = createReport(ownerBearer);
        UUID photoId = uploadPhotoOk(ownerBearer, reportId, "foreign-asset");

        UUID foreignAsset = UUID.randomUUID();
        when(propertyClient.asset(eq(foreignAsset))).thenReturn(Optional.of(
                new PropertyInternalClient.AssetInfo(foreignAsset, UUID.randomUUID(),
                        "assets/other/x.jpg", "d".repeat(64), assetLat, assetLng,
                        Instant.parse("2026-06-01T08:00:00Z"), "Foreign", BigDecimal.TEN, "OTHER")));

        MvcResult result = createPair(ownerBearer, reportId, photoId, foreignAsset);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void memberCanViewButNeverMutateAndOutsidersAreBlocked() throws Exception {
        UUID reportId = createReport(exportBearer); // MEMBER_EXPORT can create

        mockMvc.perform(get("/api/v1/damage-reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer))
                .andExpect(status().isOk());

        MvcResult memberUpload = uploadPhoto(memberBearer, reportId, photoBytes("member"),
                Sha256.hex(photoBytes("member")));
        assertThat(memberUpload.getResponse().getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(memberUpload.getResponse().getContentAsString())
                .get("data").get("errorCode").asString()).isEqualTo("NOT_OWNER");

        mockMvc.perform(post("/api/v1/properties/{id}/damage-reports", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disasterType\":\"FLOOD\",\"occurredAt\":\"2026-06-11T06:00:00Z\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("NOT_OWNER"));

        mockMvc.perform(put("/api/v1/damage-reports/{id}/complete", reportId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/damage-reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, outsiderBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.errorCode").value("NOT_MEMBER"));
    }

    @Test
    void completingAnEmptyReportIs400() throws Exception {
        UUID reportId = createReport(ownerBearer);
        mockMvc.perform(put("/api/v1/damage-reports/{id}/complete", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("EMPTY_REPORT"));
    }

    @Test
    void suggestionLookupFailureNeverLosesThePhoto() throws Exception {
        UUID reportId = createReport(ownerBearer);
        when(propertyClient.assetsNear(any(), any(), any(), anyDouble()))
                .thenThrow(new RuntimeException("property-service unreachable"));

        byte[] bytes = photoBytes("resilient");
        MvcResult result = uploadPhoto(ownerBearer, reportId, bytes, Sha256.hex(bytes));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("pairingSuggestions")).isEmpty();
        assertThat(data.get("photo").get("id").asString()).isNotBlank();
    }

    @Test
    void occurredAtInTheFutureIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/properties/{id}/damage-reports", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disasterType\":\"FIRE\",\"occurredAt\":\"2030-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.fields.occurredAt").isNotEmpty());
    }

    @Test
    void onDemandSuggestionsWorkForTheManualPairingScreen() throws Exception {
        UUID reportId = createReport(ownerBearer);
        UUID photoId = uploadPhotoOk(ownerBearer, reportId, "manual-screen");

        mockMvc.perform(get("/api/v1/damage-reports/{id}/photos/{photoId}/pairing-suggestions",
                        reportId, photoId)
                        .param("radiusM", "100")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pairingSuggestions[0].assetId").value(assetId.toString()));
    }

    @Test
    void deletingAPairIsAllowedOnlyWhileDraft() throws Exception {
        UUID reportId = createReport(ownerBearer);
        UUID photoId = uploadPhotoOk(ownerBearer, reportId, "to-unpair");
        MvcResult paired = createPair(ownerBearer, reportId, photoId, assetId);
        UUID pairId = UUID.fromString(objectMapper.readTree(paired.getResponse().getContentAsString())
                .get("data").get("id").asString());

        mockMvc.perform(delete("/api/v1/damage-reports/{id}/pairs/{pairId}", reportId, pairId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        // re-pair, complete, then deletion must be blocked
        MvcResult repaired = createPair(ownerBearer, reportId, photoId, assetId);
        UUID pairId2 = UUID.fromString(objectMapper.readTree(repaired.getResponse().getContentAsString())
                .get("data").get("id").asString());
        mockMvc.perform(put("/api/v1/damage-reports/{id}/complete", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/damage-reports/{id}/pairs/{pairId}", reportId, pairId2)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void internalApiIsKeyGuardedAndReturnsObjectPaths() throws Exception {
        UUID reportId = createReport(ownerBearer);
        UUID photoId = uploadPhotoOk(ownerBearer, reportId, "internal");
        createPair(ownerBearer, reportId, photoId, assetId);

        mockMvc.perform(get("/internal/damage-reports/{id}", reportId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/internal/damage-reports/{id}", reportId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.propertyId").value(propertyId.toString()))
                .andExpect(jsonPath("$.data.createdByUserId").value(ownerId.toString()))
                .andExpect(jsonPath("$.data.photos[0].objectPath",
                        org.hamcrest.Matchers.startsWith("damage/")))
                .andExpect(jsonPath("$.data.pairs[0].before.photoUrl",
                        org.hamcrest.Matchers.startsWith("assets/")));
    }

    @Test
    void myReportsListsWhatICreated() throws Exception {
        UUID reportId = createReport(ownerBearer);
        uploadPhotoOk(ownerBearer, reportId, "mine");

        mockMvc.perform(get("/api/v1/users/me/damage-reports")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].propertyId").value(propertyId.toString()))
                .andExpect(jsonPath("$.data.items[0].photoCount").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").isNumber());
    }
}
