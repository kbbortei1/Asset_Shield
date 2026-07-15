package com.assetshield.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Password reset (forgot/reset) and immediate account purge semantics. */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "SUPERADMIN_PHONE=" + TestProps.SUPERADMIN_PHONE,
        "SUPERADMIN_PASSWORD=" + TestProps.SUPERADMIN_PASSWORD,
        "OTP_DEV_CODE=" + TestProps.DEV_CODE,
        "SMS_PROVIDER=mock",
        "STORAGE_PROVIDER=local",
        "STORAGE_LOCAL_ROOT=target/it-storage-auth-reset"
})
@AutoConfigureMockMvc
@Testcontainers
class PasswordResetAndPurgeIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // ── helpers ──────────────────────────────────────────────────────────────

    private JsonNode registerAndActivate(String phone, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","password":"%s","fullName":"Reset Tester"}
                                """.formatted(phone, password)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","code":"%s"}
                                """.formatted(phone, TestProps.DEV_CODE)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
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

    // ── password reset ───────────────────────────────────────────────────────

    @Test
    void forgotThenResetChangesPasswordAndRevokesSessions() throws Exception {
        String phone = "+233244000101";
        JsonNode tokens = registerAndActivate(phone, "OldPassword#1");
        String refreshToken = tokens.get("refreshToken").asString();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpSent").value(true));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","code":"%s","newPassword":"NewPassword#2"}
                                """.formatted(phone, TestProps.DEV_CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // every pre-reset session is revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        // old password dead, new password works
        JsonNode bad = login(phone, "OldPassword#1", 401);
        assertThat(bad.get("data").get("errorCode").asString()).isEqualTo("BAD_CREDENTIALS");
        login(phone, "NewPassword#2", 200);
    }

    @Test
    void forgotPasswordForUnknownPhoneLooksIdenticalAndResetFails() throws Exception {
        // Response must not reveal whether the number is registered.
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+233244999901\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpSent").value(true));

        // ...but no code was actually issued, so a reset attempt fails.
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+233244999901","code":"%s","newPassword":"Whatever#1"}
                                """.formatted(TestProps.DEV_CODE)))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.data.errorCode").value("OTP_INVALID"));
    }

    @Test
    void resetPasswordValidatesTheNewPassword() throws Exception {
        String phone = "+233244000102";
        registerAndActivate(phone, "OldPassword#1");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","code":"%s","newPassword":"short"}
                                """.formatted(phone, TestProps.DEV_CODE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.fields.newPassword").isNotEmpty());
    }

    // ── purge ────────────────────────────────────────────────────────────────

    @Test
    void purgeDeactivatesImmediatelyFreesThePhoneAndBlocksOldLogin() throws Exception {
        String phone = "+233244000103";
        JsonNode tokens = registerAndActivate(phone, "Password#1");
        String accessToken = tokens.get("accessToken").asString();

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purgeScheduled").value(true));

        // the old credentials are gone for good
        JsonNode body = login(phone, "Password#1", 401);
        assertThat(body.get("data").get("errorCode").asString()).isEqualTo("BAD_CREDENTIALS");

        // the phone number is freed: a brand-new account can register with it
        JsonNode fresh = registerAndActivate(phone, "Fresh#Password1");
        assertThat(fresh.get("accessToken").asString()).isNotEmpty();
        login(phone, "Fresh#Password1", 200);
    }
}
