package com.assetshield.damage.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.damage.TestProps;
import com.assetshield.damage.TestTokens;
import com.assetshield.damage.client.AccessLevel;
import com.assetshield.damage.client.AuthUserClient;
import com.assetshield.damage.client.PaymentServiceClient;
import com.assetshield.damage.client.PropertyInternalClient;
import com.assetshield.damage.domain.Dossier;
import com.assetshield.damage.domain.DossierStatus;
import com.assetshield.damage.repo.DossierRepository;
import com.assetshield.damage.service.Sha256;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
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
        "STORAGE_LOCAL_ROOT=" + DossierFlowIT.STORAGE_ROOT,
        "PAIRING_RADIUS_METERS=25",
        "DOSSIER_FEE_GHS=50.00",
        "PROPERTY_SERVICE_URL=http://property-service.invalid",
        "PAYMENT_SERVICE_URL=http://payment-service.invalid",
        "AUTH_SERVICE_URL=http://auth-service.invalid"
})
@AutoConfigureMockMvc
@Testcontainers
class DossierFlowIT {

    static final String STORAGE_ROOT = "target/it-storage-dossier";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DossierRepository dossierRepository;

    @MockitoBean
    PropertyInternalClient propertyClient;

    @MockitoBean
    PaymentServiceClient paymentClient;

    @MockitoBean
    AuthUserClient authUserClient;

    final UUID propertyId = UUID.randomUUID();
    final UUID assetId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final String ownerBearer = TestTokens.bearer(ownerId, "+233244400001");

    String assetObjectPath;
    String assetHash;

    @BeforeEach
    void stubClients() throws Exception {
        when(propertyClient.access(any(), any())).thenReturn(AccessLevel.NONE);
        when(propertyClient.access(eq(propertyId), eq(ownerId))).thenReturn(AccessLevel.OWNER);
        when(propertyClient.property(any())).thenAnswer(invocation ->
                propertyId.equals(invocation.getArgument(0))
                        ? Optional.of(new PropertyInternalClient.PropertyInfo(propertyId, ownerId,
                        "Adabraka Flat", "RESIDENTIAL", "Adabraka", false, false))
                        : Optional.empty());
        when(authUserClient.byId(any())).thenReturn(Optional.of(
                new AuthUserClient.AuthUserInfo(ownerId, "Kwesi Boateng", "+233244400001")));
        when(paymentClient.initializeDossierFee(any(), any(), any(), any())).thenAnswer(invocation ->
                new PaymentServiceClient.PaymentInit(UUID.randomUUID(),
                        "ASGH-DSR-" + UUID.randomUUID().toString().substring(0, 12),
                        "http://localhost:8080/mock-checkout/test"));

        // a real "before" image stored where the local provider will load it from
        byte[] beforeImage = jpeg(Color.GREEN, 320, 240);
        assetObjectPath = "assets/fixture/before-" + assetId + ".jpg";
        assetHash = Sha256.hex(beforeImage);
        Path target = Path.of(STORAGE_ROOT, assetObjectPath);
        Files.createDirectories(target.getParent());
        Files.write(target, beforeImage);

        when(propertyClient.asset(eq(assetId))).thenReturn(Optional.of(
                new PropertyInternalClient.AssetInfo(assetId, propertyId, assetObjectPath, assetHash,
                        new BigDecimal("5.546111"), new BigDecimal("-0.211667"),
                        Instant.parse("2026-06-01T08:00:00Z"), "Samsung TV",
                        new BigDecimal("3500.00"), "ELECTRONICS")));
        when(propertyClient.assetsNear(any(), any(), any(), anyDouble())).thenReturn(List.of());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    static byte[] jpeg(Color color, int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, w, h);
        graphics.setColor(Color.BLACK);
        graphics.drawLine(0, 0, w, h);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private UUID createCompletedReport() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/properties/{id}/damage-reports", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disasterType\":\"FIRE\",\"occurredAt\":\"2026-06-11T06:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reportId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asString());

        byte[] afterImage = jpeg(Color.RED, 320, 240);
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/damage-reports/{id}/photos", reportId)
                        .file(new MockMultipartFile("file", "after.jpg", "image/jpeg", afterImage))
                        .file(new MockMultipartFile("metadata", "metadata", "application/json",
                                """
                                {"sha256Hash":"%s","gpsLat":5.546120,"gpsLng":-0.211670,
                                 "capturedAt":"2026-06-11T07:00:00Z","description":"burnt TV"}
                                """.formatted(Sha256.hex(afterImage)).getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isCreated())
                .andReturn();
        UUID photoId = UUID.fromString(objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get("data").get("photo").get("id").asString());

        mockMvc.perform(post("/api/v1/damage-reports/{id}/pairs", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"damagePhotoId":"%s","assetId":"%s","pairingMethod":"GPS_AUTO"}
                                """.formatted(photoId, assetId)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/damage-reports/{id}/complete", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk());
        return reportId;
    }

    private UUID requestDossier(UUID reportId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/damage-reports/{id}/generate-dossier", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.payment.amount").value(50.00))
                .andExpect(jsonPath("$.data.payment.authorizationUrl").isNotEmpty())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("dossierId").asString());
    }

    private void confirmPayment(UUID dossierId, UUID paymentId) throws Exception {
        mockMvc.perform(post("/internal/dossiers/{id}/payment-confirmed", dossierId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + paymentId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));
    }

    private Dossier awaitTerminalStatus(UUID dossierId) throws Exception {
        for (int i = 0; i < 60; i++) {
            Dossier dossier = dossierRepository.findById(dossierId).orElseThrow();
            if (dossier.getStatus() == DossierStatus.READY
                    || dossier.getStatus() == DossierStatus.FAILED) {
                return dossier;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Dossier did not reach a terminal status in 30s");
    }

    // ── flows ────────────────────────────────────────────────────────────────

    @Test
    void dossierOnDraftReportIsRejected() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/properties/{id}/damage-reports", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disasterType\":\"FLOOD\",\"occurredAt\":\"2026-06-11T06:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reportId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asString());

        mockMvc.perform(post("/api/v1/damage-reports/{id}/generate-dossier", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void fullLifecyclePaymentGenerationDownloadAndShares() throws Exception {
        UUID reportId = createCompletedReport();
        UUID dossierId = requestDossier(reportId);

        // duplicate request → 409 with the existing dossier (fresh checkout attached)
        mockMvc.perform(post("/api/v1/damage-reports/{id}/generate-dossier", reportId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("DOSSIER_EXISTS"))
                .andExpect(jsonPath("$.data.fields.dossierId").value(dossierId.toString()))
                .andExpect(jsonPath("$.data.fields.authorizationUrl").isNotEmpty());

        // download before paying → 402
        mockMvc.perform(get("/api/v1/dossiers/{id}/download", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.data.errorCode").value("PAYMENT_REQUIRED"));

        // settle → async generation → READY
        UUID paymentId = UUID.randomUUID();
        confirmPayment(dossierId, paymentId);
        Dossier dossier = awaitTerminalStatus(dossierId);
        assertThat(dossier.getStatus()).isEqualTo(DossierStatus.READY);
        assertThat(dossier.getManifestHash()).hasSize(64);
        assertThat(dossier.getPageCount()).isGreaterThanOrEqualTo((short) 4);
        assertThat(dossier.getTotalEstimatedLoss()).isEqualByComparingTo("3500.00");

        // replayed payment-confirmed → idempotent, still READY
        confirmPayment(dossierId, paymentId);
        assertThat(dossierRepository.findById(dossierId).orElseThrow().getStatus())
                .isEqualTo(DossierStatus.READY);

        // status endpoint mirrors the row
        mockMvc.perform(get("/api/v1/dossiers/{id}/status", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.manifestHash").value(dossier.getManifestHash()));

        // download → signed URL streams a real PDF
        MvcResult download = mockMvc.perform(get("/api/v1/dossiers/{id}/download", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName",
                        org.hamcrest.Matchers.startsWith("AssetShield_Dossier_AdabrakaFlat_")))
                .andReturn();
        String downloadUrl = objectMapper.readTree(download.getResponse().getContentAsString())
                .get("data").get("downloadUrl").asString();
        byte[] pdf = mockMvc.perform(get(downloadUrl))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // share link works logged-out, READY only
        UUID shareToken = dossier.getShareToken();
        mockMvc.perform(get("/api/v1/dossiers/shared/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.propertyName").value("Adabraka Flat"))
                .andExpect(jsonPath("$.data.manifestHash").value(dossier.getManifestHash()));

        // rotation kills the leaked link
        MvcResult rotated = mockMvc.perform(post("/api/v1/dossiers/{id}/rotate-share-token", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andReturn();
        UUID newToken = UUID.fromString(objectMapper.readTree(rotated.getResponse().getContentAsString())
                .get("data").get("shareToken").asString());
        mockMvc.perform(get("/api/v1/dossiers/shared/{token}", shareToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/dossiers/shared/{token}", newToken))
                .andExpect(status().isOk());

        // retry is only allowed from FAILED
        mockMvc.perform(post("/api/v1/dossiers/{id}/retry-generation", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("INVALID_STATE_TRANSITION"));

        // my-dossiers lists it
        mockMvc.perform(get("/api/v1/users/me/dossiers")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].propertyName").value("Adabraka Flat"))
                .andExpect(jsonPath("$.data.items[0].status").value("READY"));
    }

    @Test
    void pendingDossierShareTokenLeaksNothing() throws Exception {
        UUID reportId = createCompletedReport();
        UUID dossierId = requestDossier(reportId);
        UUID shareToken = dossierRepository.findById(dossierId).orElseThrow().getShareToken();

        mockMvc.perform(get("/api/v1/dossiers/shared/{token}", shareToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/dossiers/shared/{token}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void verifyDetectsCorruptedStoredObjects() throws Exception {
        UUID reportId = createCompletedReport();
        UUID dossierId = requestDossier(reportId);
        confirmPayment(dossierId, UUID.randomUUID());
        Dossier dossier = awaitTerminalStatus(dossierId);
        assertThat(dossier.getStatus()).isEqualTo(DossierStatus.READY);

        // clean dossier → intact
        mockMvc.perform(get("/internal/dossiers/{id}/verify", dossierId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tamperEvident").value(true))
                .andExpect(jsonPath("$.data.recomputedHash").value(dossier.getManifestHash()))
                .andExpect(jsonPath("$.data.mismatches").isEmpty());

        // corrupt the stored BEFORE image → detected with the mismatch listed
        Files.write(Path.of(STORAGE_ROOT, assetObjectPath),
                "tampered-bytes".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/internal/dossiers/{id}/verify", dossierId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tamperEvident").value(false))
                .andExpect(jsonPath("$.data.mismatches[0].objectPath").value(assetObjectPath))
                .andExpect(jsonPath("$.data.mismatches[0].expected").value(assetHash));
    }

    @Test
    void internalEndpointsRequireTheApiKey() throws Exception {
        UUID reportId = createCompletedReport();
        UUID dossierId = requestDossier(reportId);

        mockMvc.perform(post("/internal/dossiers/{id}/payment-confirmed", dossierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/dossiers/{id}/meta", dossierId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.propertyId").value(propertyId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
    }
}
