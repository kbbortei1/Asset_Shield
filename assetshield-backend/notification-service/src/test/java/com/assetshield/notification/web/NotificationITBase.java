package com.assetshield.notification.web;

import com.assetshield.notification.MutableClock;
import com.assetshield.notification.TestProps;
import com.assetshield.notification.client.PropertyClient;
import com.assetshield.notification.push.PushSender;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared Spring context for the Day 6 ITs: one PostgreSQL container for the
 * whole suite (singleton, deliberately NOT @Container so the JUnit extension
 * never stops it between classes), property-service mocked, the real
 * LogPushSender wrapped in a spy, schedule crons disabled ("-") so only the
 * tests drive the sweeps, and a mutable Africa/Accra clock.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "FCM_MODE=log",
        "TIPS_BATCH_SIZE=3",
        "TIPS_DEBOUNCE_MINUTES=60",
        "SCHED_TIP_DELIVERY_CRON=-",
        "SCHED_REDOC_CRON=-",
        "REDOC_STALE_DAYS=90"
})
@AutoConfigureMockMvc
@Import(NotificationITBase.TestClockConfig.class)
public abstract class NotificationITBase {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    static {
        postgres.start();
    }

    @TestConfiguration
    public static class TestClockConfig {

        @Bean
        @Primary
        public MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MutableClock clock;

    @MockitoBean
    protected PropertyClient propertyClient;

    @MockitoSpyBean
    protected PushSender pushSender;

    // ── context fixtures ─────────────────────────────────────────────────────

    /** Kantamanto market coordinates — NOT inside any seeded flood zone. */
    protected static final BigDecimal KANTAMANTO_LAT = new BigDecimal("5.546111");
    protected static final BigDecimal KANTAMANTO_LNG = new BigDecimal("-0.211667");

    /** Centre of the seeded Kaneshie flood-zone box. */
    protected static final BigDecimal KANESHIE_LAT = new BigDecimal("5.566700");
    protected static final BigDecimal KANESHIE_LNG = new BigDecimal("-0.233300");

    protected static PropertyClient.TipsContext context(UUID propertyId, UUID ownerId,
                                                        String propertyType,
                                                        BigDecimal lat, BigDecimal lng,
                                                        PropertyClient.CategoryLine... lines) {
        return new PropertyClient.TipsContext(propertyId, ownerId, propertyType, lat, lng,
                List.of(lines));
    }

    protected static PropertyClient.CategoryLine line(String category, long count, String value) {
        return new PropertyClient.CategoryLine(category, count, new BigDecimal(value));
    }
}
