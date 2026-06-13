package com.assetshield.property.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.property.TestProps;
import com.assetshield.property.client.AuthUserClient;
import com.assetshield.property.domain.Property;
import com.assetshield.property.domain.PropertyType;
import com.assetshield.property.repo.PropertyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The Day 5 internal leads list: opted-in live properties, filtered. */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "STORAGE_PROVIDER=local",
        "STORAGE_LOCAL_ROOT=target/it-storage-leads",
        "TIER_LOOKUP_MODE=stub",
        "STUB_TIER=PRO",
        "NOTIFICATIONS_MODE=log",
        "AUTH_SERVICE_URI=http://auth-service.invalid"
})
@AutoConfigureMockMvc
@Testcontainers
class InternalLeadsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PropertyRepository propertyRepository;

    @MockitoBean
    AuthUserClient authUserClient;

    UUID ownerId;

    @BeforeEach
    void seed() {
        propertyRepository.deleteAll();
        ownerId = UUID.randomUUID();
        when(authUserClient.byId(any(UUID.class)))
                .thenReturn(Optional.of(new AuthUserClient.AuthUserInfo(
                        ownerId, "Ama Serwaa Owusu", "+233200000001", "OWNER", "ACTIVE")));
        property("Adabraka Lodge", PropertyType.RESIDENTIAL, "Adabraka", true, false);
        property("Osu Kiosk", PropertyType.COMMERCIAL, "Osu", true, false);
        property("Hidden House", PropertyType.RESIDENTIAL, "Labone", false, false);
        property("Gone Lodge", PropertyType.RESIDENTIAL, "Adabraka", true, true);
    }

    private void property(String name, PropertyType type, String locality,
                          boolean optedIn, boolean deleted) {
        Property p = new Property();
        p.setOwnerUserId(ownerId);
        p.setName(name);
        p.setType(type);
        p.setLocality(locality);
        p.setGpsLat(new BigDecimal("5.6037"));
        p.setGpsLng(new BigDecimal("-0.1870"));
        p.setOpenToOffers(optedIn);
        if (optedIn) {
            p.setOpenToOffersAt(Instant.now());
        }
        if (deleted) {
            p.setDeletedAt(Instant.now());
        }
        propertyRepository.save(p);
    }

    @Test
    void onlyLiveOptedInPropertiesAppearWithReducedOwnerNames() throws Exception {
        mockMvc.perform(get("/internal/properties/leads")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[*].propertyName").value(
                        org.hamcrest.Matchers.containsInAnyOrder("Adabraka Lodge", "Osu Kiosk")))
                // first name + last initial — never the full name
                .andExpect(jsonPath("$.data.items[0].ownerDisplayName").value("Ama O."));
    }

    @Test
    void typeAndLocalityFiltersNarrowTheList() throws Exception {
        mockMvc.perform(get("/internal/properties/leads")
                        .param("propertyType", "COMMERCIAL")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].propertyName").value("Osu Kiosk"));

        // locality is a case-insensitive contains
        mockMvc.perform(get("/internal/properties/leads")
                        .param("locality", "dabRA")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].propertyName").value("Adabraka Lodge"));

        // unknown enum value matches nothing instead of erroring
        mockMvc.perform(get("/internal/properties/leads")
                        .param("propertyType", "CASTLE")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void internalLeadsRequireTheApiKey() throws Exception {
        mockMvc.perform(get("/internal/properties/leads"))
                .andExpect(status().isUnauthorized());
    }
}
