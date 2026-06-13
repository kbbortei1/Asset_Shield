package com.assetshield.marketplace.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetshield.marketplace.TestProps;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The four partial unique indexes are load-bearing consent/billing
 * guarantees — exercised here at the SQL level, bypassing every service
 * pre-check.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "PAYMENTS_MODE=mock",
        "MOCK_AUTO_SETTLE_MS=-1"
})
@Testcontainers
class PartialIndexIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    JdbcTemplate jdbc;

    private UUID insertAgent() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO insurance_agents (id, user_id, insurer_name, nic_licence_no, verification_status)
                VALUES (?, ?, 'Star', ?, 'VERIFIED')
                """, id, UUID.randomUUID(), "NIC-" + UUID.randomUUID());
        return id;
    }

    private void insertAgentSub(UUID agentId, String status) {
        jdbc.update("""
                INSERT INTO agent_subscriptions (agent_id, status, started_at, expires_at)
                VALUES (?, ?, now(), now() + interval '30 days')
                """, agentId, status);
    }

    private UUID insertInterest(UUID agentId, UUID propertyId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO agent_interests (id, agent_id, property_id, owner_user_id, status)
                VALUES (?, ?, ?, ?, ?)
                """, id, agentId, propertyId, UUID.randomUUID(), status);
        return id;
    }

    private void insertShare(UUID dossierId, UUID agentId, UUID interestId, OffsetDateTime revokedAt) {
        jdbc.update("""
                INSERT INTO dossier_shares (dossier_id, agent_id, agent_interest_id, shared_by_user_id, revoked_at)
                VALUES (?, ?, ?, ?, ?)
                """, dossierId, agentId, interestId, UUID.randomUUID(), revokedAt);
    }

    @Test
    void onlyOneActiveAgentSubscriptionPerAgent() {
        UUID agentId = insertAgent();
        insertAgentSub(agentId, "ACTIVE");
        assertThatThrownBy(() -> insertAgentSub(agentId, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // a lapsed history row does not block a new ACTIVE one
        jdbc.update("UPDATE agent_subscriptions SET status = 'EXPIRED' WHERE agent_id = ?", agentId);
        insertAgentSub(agentId, "ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM agent_subscriptions WHERE agent_id = ?", Integer.class, agentId))
                .isEqualTo(2);
    }

    @Test
    void onlyOnePendingInterestPerAgentAndProperty() {
        UUID agentId = insertAgent();
        UUID propertyId = UUID.randomUUID();
        insertInterest(agentId, propertyId, "PENDING");
        assertThatThrownBy(() -> insertInterest(agentId, propertyId, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // DECLINED history permits a fresh PENDING
        jdbc.update("UPDATE agent_interests SET status = 'DECLINED' WHERE agent_id = ?", agentId);
        insertInterest(agentId, propertyId, "PENDING");
    }

    @Test
    void onlyOneLiveSharePerDossierAndAgent() {
        UUID agentId = insertAgent();
        UUID interestId = insertInterest(agentId, UUID.randomUUID(), "ACCEPTED");
        UUID dossierId = UUID.randomUUID();
        insertShare(dossierId, agentId, interestId, null);
        assertThatThrownBy(() -> insertShare(dossierId, agentId, interestId, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // a revoked share frees the slot for a re-share
        jdbc.update("UPDATE dossier_shares SET revoked_at = now() WHERE dossier_id = ?", dossierId);
        insertShare(dossierId, agentId, interestId, null);
    }

    @Test
    void onlyOneActiveProSubscriptionPerUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_subscriptions (user_id, status, started_at, expires_at)
                VALUES (?, 'ACTIVE', now(), now() + interval '30 days')
                """, userId);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO user_subscriptions (user_id, status, started_at, expires_at)
                VALUES (?, 'ACTIVE', now(), now() + interval '30 days')
                """, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE user_subscriptions SET status = 'CANCELLED' WHERE user_id = ?", userId);
        jdbc.update("""
                INSERT INTO user_subscriptions (user_id, status, started_at, expires_at)
                VALUES (?, 'ACTIVE', now(), now() + interval '30 days')
                """, userId);
    }
}
