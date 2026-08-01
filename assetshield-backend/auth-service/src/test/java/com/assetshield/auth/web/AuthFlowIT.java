package com.assetshield.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.auth.TestProps;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "SUPERADMIN_PHONE=" + TestProps.SUPERADMIN_PHONE,
        "SUPERADMIN_PASSWORD=" + TestProps.SUPERADMIN_PASSWORD,
        "OTP_DEV_CODE=" + TestProps.DEV_CODE,
        "STORAGE_PROVIDER=local",
        "STORAGE_LOCAL_ROOT=target/it-storage-auth"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // â”€â”€ helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** A unique email per phone so the ux_users_email constraint is satisfied. */
    private static String emailFor(String phone) {
        return "u" + phone.replaceAll("[^0-9]", "") + "@test.assetshield.app";
    }

    private JsonNode register(String phone, String password, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","email":"%s","password":"%s","fullName":"%s"}
                                """.formatted(phone, emailFor(phone), password, fullName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.otpSent").value(true))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode verifyOtp(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","code":"%s"}
                                """.formatted(phone, TestProps.DEV_CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.data.user.role").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode registerAndActivate(String phone) throws Exception {
        register(phone, "Password#1", "Ama Mensah");
        return verifyOtp(phone);
    }

    private JsonNode login(String phone, String password, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // â”€â”€ flows â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void happyPathRegisterVerifyMeRefreshLogout() throws Exception {
        String phone = "+233244000001";
        JsonNode tokens = registerAndActivate(phone);
        String accessToken = tokens.get("accessToken").asString();
        String refreshToken = tokens.get("refreshToken").asString();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.phoneNumber").value(phone))
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.ghanaCardUploaded").value(false))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        String rotatedRefresh = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .get("data").get("refreshToken").asString();
        assertThat(rotatedRefresh).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotatedRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loggedOut").value(true));

        // Logout revoked the family: the rotated token is dead now.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotatedRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.errorCode").value("REFRESH_REUSED"));
    }

    @Test
    void reusingARotatedRefreshTokenBurnsTheFamily() throws Exception {
        JsonNode tokens = registerAndActivate("+233244000002");
        String original = tokens.get("refreshToken").asString();

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + original + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String successor = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .get("data").get("refreshToken").asString();

        // Replaying the original token = reuse â†’ 401 and the family burns.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + original + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.errorCode").value("REFRESH_REUSED"));

        // The (previously valid) successor is collateral damage.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + successor + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.errorCode").value("REFRESH_REUSED"));
    }

    @Test
    void loginBeforeOtpVerificationRequiresOtp() throws Exception {
        String phone = "+233244000003";
        register(phone, "Password#1", "Yaw Owusu");

        JsonNode body = login(phone, "Password#1", 401);
        assertThat(body.get("status").asString()).isEqualTo("error");
        assertThat(body.get("data").get("errorCode").asString()).isEqualTo("OTP_REQUIRED");
    }

    @Test
    void wrongPasswordReturnsBadCredentials() throws Exception {
        String phone = "+233244000004";
        registerAndActivate(phone);

        JsonNode body = login(phone, "wrong-password", 401);
        assertThat(body.get("data").get("errorCode").asString()).isEqualTo("BAD_CREDENTIALS");
        assertThat(body.get("message").asString()).isEqualTo("Invalid phone number or password");

        // Unknown phone gets the identical message (no user enumeration).
        JsonNode unknown = login("+233244999998", "wrong-password", 401);
        assertThat(unknown.get("message").asString()).isEqualTo("Invalid phone number or password");
    }

    @Test
    void duplicateActivePhoneIsRejectedWithEnvelope() throws Exception {
        String phone = "+233244000005";
        registerAndActivate(phone);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","email":"%s","password":"Password#1","fullName":"Esi Arthur"}
                                """.formatted(phone, emailFor(phone))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.errorCode").value("PHONE_EXISTS"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void pendingOtpReRegistrationIsNot409ButOtpReIssueIsThrottled() throws Exception {
        String phone = "+233244000013";
        register(phone, "Password#1", "First Name");
        // Re-registering while PENDING_OTP updates the user instead of 409,
        // but the immediate OTP re-issue is throttled (< 60s) â†’ 429.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","email":"%s","password":"Password#2","fullName":"Second Name"}
                                """.formatted(phone, emailFor(phone))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.data.errorCode").value("OTP_THROTTLED"));
    }

    @Test
    void resendOtpIsThrottledAndRequiresPendingUser() throws Exception {
        String phone = "+233244000006";
        register(phone, "Password#1", "Abena Sarpong");

        mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.data.errorCode").value("OTP_THROTTLED"));

        mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+233244999997\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void validationFailuresReportFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"0244123456","password":"short","fullName":"A"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.fields.phoneNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.fields.password").isNotEmpty())
                .andExpect(jsonPath("$.data.fields.fullName").isNotEmpty());
    }

    @Test
    void agentRegistrationStoresDetailsAndRejectsDuplicateLicence() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+233244000007","password":"Password#1","fullName":"Agent One",
                                 "insurerName":"Star Assurance","nicLicenceNo":"NIC-001-A"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.otpSent").value(true));

        mockMvc.perform(post("/api/v1/auth/register-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+233244000008","password":"Password#1","fullName":"Agent Two",
                                 "insurerName":"Star Assurance","nicLicenceNo":"NIC-001-A"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("LICENCE_EXISTS"));
    }

    @Test
    void updateProfileAndPurgeFlow() throws Exception {
        String phone = "+233244000009";
        JsonNode tokens = registerAndActivate(phone);
        String accessToken = tokens.get("accessToken").asString();
        String refreshToken = tokens.get("refreshToken").asString();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ama Updated\",\"language\":\"tw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Ama Updated"))
                .andExpect(jsonPath("$.data.language").value("tw"));

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purgeScheduled").value(true))
                .andExpect(jsonPath("$.data.deadline").isNotEmpty());

        // All refresh tokens are revoked once purge is requested.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void superadminCanCreateAdminButOwnerCannot() throws Exception {
        JsonNode adminLogin = login(TestProps.SUPERADMIN_PHONE, TestProps.SUPERADMIN_PASSWORD, 200);
        String adminToken = adminLogin.get("data").get("accessToken").asString();

        mockMvc.perform(post("/api/v1/admin/admins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+233244000010","password":"AdminPass#1","fullName":"Second Admin"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").isNotEmpty());

        JsonNode ownerTokens = registerAndActivate("+233244000011");
        mockMvc.perform(post("/api/v1/admin/admins")
                        .header("Authorization", "Bearer " + ownerTokens.get("accessToken").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+233244000012","password":"AdminPass#1","fullName":"Evil Admin"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.errorCode").value("FORBIDDEN"));
    }

    @Test
    void ghanaCardUploadFlagsTheProfileAndRejectsWrongTypes() throws Exception {
        JsonNode tokens = registerAndActivate("+233244000015");
        String accessToken = tokens.get("accessToken").asString();

        mockMvc.perform(multipart("/api/v1/users/me/ghana-card")
                        .file(new MockMultipartFile("file", "card.jpg", "image/jpeg",
                                "fake-jpeg-ghana-card".getBytes()))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.ghanaCardUploaded").value(true));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ghanaCardUploaded").value(true));

        mockMvc.perform(multipart("/api/v1/users/me/ghana-card")
                        .file(new MockMultipartFile("file", "card.pdf", "application/pdf",
                                "%PDF-1.4".getBytes()))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.data.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.errorCode").value("TOKEN_INVALID"));
    }

    @Test
    void internalApiRequiresTheApiKey() throws Exception {
        JsonNode tokens = registerAndActivate("+233244000014");
        String userId = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/users/me")
                                        .header("Authorization", "Bearer " + tokens.get("accessToken").asString()))
                                .andReturn().getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(get("/internal/users/" + userId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/internal/users/" + userId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneNumber").value("+233244000014"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/internal/users/by-phone/+233244000014")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId));
    }
}
