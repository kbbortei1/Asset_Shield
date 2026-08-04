package com.assetshield.property.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.property.TestProps;
import com.assetshield.property.TestTokens;
import com.assetshield.property.client.AuthUserClient;
import com.assetshield.property.service.Sha256;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        "STORAGE_LOCAL_ROOT=target/it-storage-flow",
        "TIER_LOOKUP_MODE=stub",
        "STUB_TIER=PRO",
        "NOTIFICATIONS_MODE=log",
        "AUTH_SERVICE_URI=http://auth-service.invalid"
})
@AutoConfigureMockMvc
@Testcontainers
class PropertyFlowIT {

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
    final String ownerPhone = "+233244000001";
    final String memberPhone = "+233244000002";
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

    private UUID createProperty(String bearer) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/properties")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Adabraka Flat","type":"RESIDENTIAL",
                                 "gpsLat":5.6037,"gpsLng":-0.1870,"locality":"Adabraka"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.assetCount").value(0))
                .andExpect(jsonPath("$.data.totalEstimatedValue").value(0))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asString());
    }

    private static String metadata(String hash, String description, String category, String value) {
        return """
                {"description":"%s","estimatedValue":%s,"category":"%s",
                 "photos":[{"sha256Hash":"%s","gpsLat":5.6037,"gpsLng":-0.1870,
                            "capturedAt":"2026-06-10T10:00:00Z"}]}
                """.formatted(description, value, category, hash);
    }

    private MvcResult upload(String bearer, UUID propertyId, byte[] bytes, String declaredHash,
                             String description, String category, String value) throws Exception {
        return mockMvc.perform(multipart("/api/v1/properties/{id}/assets", propertyId)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes))
                        .file(new MockMultipartFile("metadata", "metadata", "application/json",
                                metadata(declaredHash, description, category, value)
                                        .getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn();
    }

    private static byte[] photoBytes(String seed) {
        return ("fake-jpeg-" + seed).getBytes(StandardCharsets.UTF_8);
    }

    private UUID memberOf(UUID propertyId) throws Exception {
        mockMvc.perform(post("/api/v1/properties/{id}/invite", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteePhone\":\"" + memberPhone + "\",\"canExport\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.inviteeRegistered").value(true))
                .andReturn();

        MvcResult invitations = mockMvc.perform(get("/api/v1/users/me/invitations")
                        .header(HttpHeaders.AUTHORIZATION, memberBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].propertyName").value("Adabraka Flat"))
                .andExpect(jsonPath("$.data.items[0].ownerName").value("Kwesi Boateng"))
                .andReturn();
        UUID invitationId = UUID.fromString(objectMapper
                .readTree(invitations.getResponse().getContentAsString())
                .get("data").get("items").get(0).get("id").asString());

        mockMvc.perform(put("/api/v1/invitations/{id}/respond", invitationId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.membershipId").isNotEmpty());
        return invitationId;
    }

    // ── flows ────────────────────────────────────────────────────────────────

    @Test
    void uploadHappyPathUpdatesDashboardAndServesSignedUrl() throws Exception {
        UUID propertyId = createProperty(ownerBearer);
        byte[] bytes = photoBytes("tv");

        MvcResult uploaded = upload(ownerBearer, propertyId, bytes, Sha256.hex(bytes),
                "Samsung TV", "ELECTRONICS", "3500.00");
        assertThat(uploaded.getResponse().getStatus()).isEqualTo(201);
        JsonNode asset = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("data");
        assertThat(asset.get("photoUrl").asString()).startsWith("/api/v1/public/files/");
        assertThat(asset.get("sha256Hash").asString()).isEqualTo(Sha256.hex(bytes));

        // lastDocumentedAt set + dashboard aggregates correct
        mockMvc.perform(get("/api/v1/properties/{id}", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastDocumentedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.dashboard.assetCount").value(1))
                .andExpect(jsonPath("$.data.dashboard.totalEstimatedValue").value(3500.00))
                .andExpect(jsonPath("$.data.dashboard.byCategory[0].category").value("ELECTRONICS"))
                .andExpect(jsonPath("$.data.dashboard.byCategory[0].count").value(1))
                .andExpect(jsonPath("$.data.dashboard.byCategory[0].value").value(3500.00));

        // the signed URL actually streams the bytes (permitAll, token-gated)
        byte[] served = mockMvc.perform(get(asset.get("photoUrl").asString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(bytes);
    }

    @Test
    void tamperedHashStoresNothing() throws Exception {
        UUID propertyId = createProperty(ownerBearer);
        byte[] bytes = photoBytes("tampered");

        MvcResult result = upload(ownerBearer, propertyId, bytes,
                Sha256.hex(photoBytes("different")), "Fridge", "ELECTRONICS", "1200.00");
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("error");
        assertThat(body.get("data").get("errorCode").asString()).isEqualTo("HASH_MISMATCH");

        // no row …
        mockMvc.perform(get("/api/v1/properties/{id}/assets", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        // … and no stored object
        assertThat(Files.notExists(Path.of("target/it-storage-flow/assets/" + propertyId))).isTrue();
    }

    @Test
    void identicalPhotoTwiceIsRejectedWith409() throws Exception {
        UUID propertyId = createProperty(ownerBearer);
        byte[] bytes = photoBytes("duplicate");
        String hash = Sha256.hex(bytes);

        assertThat(upload(ownerBearer, propertyId, bytes, hash, "Sofa", "FURNITURE", "900.00")
                .getResponse().getStatus()).isEqualTo(201);

        MvcResult second = upload(ownerBearer, propertyId, bytes, hash, "Sofa again", "FURNITURE", "900.00");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(second.getResponse().getContentAsString())
                .get("data").get("errorCode").asString()).isEqualTo("DUPLICATE_ASSET_HASH");
    }

    @Test
    void householdLifecycleInviteAcceptContributeRevoke() throws Exception {
        UUID propertyId = createProperty(ownerBearer);
        byte[] ownerPhoto = photoBytes("owner-asset");
        UUID ownerAssetId = UUID.fromString(objectMapper.readTree(
                        upload(ownerBearer, propertyId, ownerPhoto, Sha256.hex(ownerPhoto),
                                "Owner TV", "ELECTRONICS", "2000.00")
                                .getResponse().getContentAsString())
                .get("data").get("id").asString());

        memberOf(propertyId);

        // member appears in the member list (owner-only view)
        mockMvc.perform(get("/api/v1/properties/{id}/members", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value(memberId.toString()))
                .andExpect(jsonPath("$.data.items[0].fullName").value("Ama Mensah"))
                .andExpect(jsonPath("$.data.items[0].canExport").value(false));

        // member list itself is owner-only
        mockMvc.perform(get("/api/v1/properties/{id}/members", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("NOT_OWNER"));

        // member contributes an asset
        byte[] memberPhoto = photoBytes("member-asset");
        MvcResult memberUpload = upload(memberBearer, propertyId, memberPhoto,
                Sha256.hex(memberPhoto), "Member chair", "FURNITURE", "150.00");
        assertThat(memberUpload.getResponse().getStatus()).isEqualTo(201);
        UUID memberAssetId = UUID.fromString(objectMapper
                .readTree(memberUpload.getResponse().getContentAsString())
                .get("data").get("id").asString());

        // member cannot edit the owner's asset …
        mockMvc.perform(put("/api/v1/assets/{id}", ownerAssetId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"hijacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("NOT_OWNER"));

        // … nor toggle the marketplace opt-in
        mockMvc.perform(put("/api/v1/properties/{id}/offers-optin", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openToOffers\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("NOT_OWNER"));

        // the owner CAN edit the member's asset
        mockMvc.perform(put("/api/v1/assets/{id}", memberAssetId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Member chair (verified)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Member chair (verified)"));

        // re-inviting an active member → 409
        mockMvc.perform(post("/api/v1/properties/{id}/invite", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteePhone\":\"" + memberPhone + "\",\"canExport\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("ALREADY_MEMBER"));

        // owner revokes → member loses access on the next read
        mockMvc.perform(delete("/api/v1/properties/{id}/members/{userId}", propertyId, memberId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(true));

        mockMvc.perform(get("/api/v1/properties/{id}", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.errorCode").value("NOT_MEMBER"));
    }

    @Test
    void respondingTwiceIs409AndStrangersAreRejected() throws Exception {
        UUID propertyId = createProperty(ownerBearer);
        UUID invitationId = memberOf(propertyId);

        mockMvc.perform(put("/api/v1/invitations/{id}/respond", invitationId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("ALREADY_RESPONDED"));

        String strangerBearer = TestTokens.bearer(UUID.randomUUID(), "+233244999999");
        mockMvc.perform(put("/api/v1/invitations/{id}/respond", invitationId)
                        .header(HttpHeaders.AUTHORIZATION, strangerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void inviteRulesOwnPhoneAndUnregisteredInvitee() throws Exception {
        UUID propertyId = createProperty(ownerBearer);

        mockMvc.perform(post("/api/v1/properties/{id}/invite", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteePhone\":\"" + ownerPhone + "\",\"canExport\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/properties/{id}/invite", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteePhone\":\"+233244888888\",\"canExport\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.inviteeRegistered").value(false));

        // duplicate pending invite for the same phone → 409
        mockMvc.perform(post("/api/v1/properties/{id}/invite", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteePhone\":\"+233244888888\",\"canExport\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("DUPLICATE_PENDING_INVITE"));
    }

    @Test
    void optInTogglesRoundTrip() throws Exception {
        UUID propertyId = createProperty(ownerBearer);

        mockMvc.perform(put("/api/v1/properties/{id}/offers-optin", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openToOffers\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openToOffers").value(true))
                .andExpect(jsonPath("$.data.openToOffersAt").isNotEmpty());

        mockMvc.perform(put("/api/v1/properties/{id}/offers-optin", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openToOffers\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openToOffers").value(false));
    }

    @Test
    void nonMemberReadsAndUnknownIdsAreRejected() throws Exception {
        UUID propertyId = createProperty(ownerBearer);
        String strangerBearer = TestTokens.bearer(UUID.randomUUID(), "+233244777777");

        mockMvc.perform(get("/api/v1/properties/{id}", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, strangerBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("NOT_MEMBER"));

        mockMvc.perform(get("/api/v1/properties/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void validationFailuresReportFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/properties")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"type\":\"RESIDENTIAL\",\"gpsLat\":99,\"gpsLng\":0,\"locality\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.fields.name").isNotEmpty())
                .andExpect(jsonPath("$.data.fields.gpsLat").isNotEmpty());
    }

    @Test
    void deletedPropertyDisappearsWithItsAssets() throws Exception {
        UUID propertyId = createProperty(ownerBearer);
        byte[] bytes = photoBytes("to-delete");
        UUID assetId = UUID.fromString(objectMapper.readTree(
                        upload(ownerBearer, propertyId, bytes, Sha256.hex(bytes),
                                "Doomed", "OTHER", "10.00")
                                .getResponse().getContentAsString())
                .get("data").get("id").asString());

        mockMvc.perform(delete("/api/v1/properties/{id}", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        mockMvc.perform(get("/api/v1/properties/{id}", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/assets/{id}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalApiIsKeyGuardedAndLeadViewExposesExactlySixFields() throws Exception {
        UUID propertyId = createProperty(ownerBearer);

        mockMvc.perform(get("/internal/properties/{id}/lead-view", propertyId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/internal/properties/{id}/lead-view", propertyId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data.propertyId").value(propertyId.toString()))
                .andExpect(jsonPath("$.data.ownerDisplayName").value("Kwesi B."))
                .andExpect(jsonPath("$.data.propertyName").value("Adabraka Flat"))
                .andExpect(jsonPath("$.data.propertyType").value("RESIDENTIAL"))
                .andExpect(jsonPath("$.data.locality").value("Adabraka"))
                .andExpect(jsonPath("$.data.openToOffers").value(false));

        mockMvc.perform(get("/internal/properties/{id}/access/{userId}", propertyId, ownerId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access").value("OWNER"));
    }

    @Test
    void assetsNearUsesBoundingBoxAndHaversine() throws Exception {
        UUID propertyId = createProperty(ownerBearer);
        byte[] near = photoBytes("near");
        upload(ownerBearer, propertyId, near, Sha256.hex(near), "Near asset", "OTHER", "10.00");

        // matching asset sits exactly at the queried point → inside 25 m
        mockMvc.perform(get("/internal/properties/{id}/assets-near", propertyId)
                        .param("lat", "5.6037").param("lng", "-0.1870").param("radiusM", "25")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].description").value("Near asset"))
                .andExpect(jsonPath("$.data.items[0].distanceMeters").isNumber());

        // 200 m away → outside a 25 m radius
        mockMvc.perform(get("/internal/properties/{id}/assets-near", propertyId)
                        .param("lat", "5.6055").param("lng", "-0.1870").param("radiusM", "25")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
