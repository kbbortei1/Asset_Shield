package com.assetshield.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.auth.TestProps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/** The audit trail records auth events (even rolled-back failures) and is admin-only. */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "SUPERADMIN_PHONE=" + TestProps.SUPERADMIN_PHONE,
        "SUPERADMIN_PASSWORD=" + TestProps.SUPERADMIN_PASSWORD,
        "OTP_DEV_CODE=" + TestProps.DEV_CODE,
        "STORAGE_PROVIDER=local",
        "STORAGE_LOCAL_ROOT=target/it-storage-audit"
})
@AutoConfigureMockMvc
@Testcontainers
class AuditIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String activateAndLogin(String phone, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\",\"email\":\"a"
                                + phone.replaceAll("[^0-9]", "") + "@test.app\",\"password\":\"" + password
                                + "\",\"fullName\":\"Audit Tester\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\",\"code\":\""
                                + TestProps.DEV_CODE + "\"}"))
                .andExpect(status().isOk());
        return login(phone, password);
    }

    private String login(String phone, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asString();
    }

    @Test
    void authEventsAreRecordedAndReadableByAdminsOnly() throws Exception {
        String phone = "+233244300001";
        String ownerToken = activateAndLogin(phone, "Password#1");

        // a failed login rolls back the request — the audit row must survive
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\",\"password\":\"WrongPass#9\"}"))
                .andExpect(status().isUnauthorized());

        // owners cannot read the trail
        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        // the (seeded) superadmin can
        String adminToken = login(TestProps.SUPERADMIN_PHONE, TestProps.SUPERADMIN_PASSWORD);
        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.action=='ACCOUNT_VERIFIED')]").isNotEmpty())
                .andExpect(jsonPath("$.data.items[?(@.action=='LOGIN_SUCCESS')]").isNotEmpty())
                .andExpect(jsonPath("$.data.items[?(@.action=='LOGIN_FAILED')]").isNotEmpty());

        // the action filter narrows the list
        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .param("action", "LOGIN_FAILED").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.data.items[0].target").value(phone))
                .andExpect(jsonPath("$.data.items[?(@.action!='LOGIN_FAILED')]").isEmpty());

        // unauthenticated → 401
        mockMvc.perform(get("/api/v1/admin/audit-events"))
                .andExpect(status().isUnauthorized());
    }
}
