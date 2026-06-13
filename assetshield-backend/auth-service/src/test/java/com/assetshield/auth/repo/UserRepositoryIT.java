package com.assetshield.auth.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetshield.auth.TestProps;
import com.assetshield.auth.domain.Role;
import com.assetshield.auth.domain.User;
import com.assetshield.auth.domain.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the Flyway schema, in particular the partial unique index on
 * phone_number (soft-deleted phones can be re-registered, live duplicates
 * cannot).
 */
@DataJpaTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    UserRepository userRepository;

    private static User user(String phone) {
        User user = new User();
        user.setPhoneNumber(phone);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyz012345678901234567890123456");
        user.setFullName("Kofi Boateng");
        user.setRole(Role.OWNER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    void softDeletedPhoneCanBeReRegistered() {
        User original = userRepository.saveAndFlush(user("+233244111111"));
        original.setDeletedAt(Instant.now());
        userRepository.saveAndFlush(original);

        User reRegistered = userRepository.saveAndFlush(user("+233244111111"));

        assertThat(reRegistered.getId()).isNotEqualTo(original.getId());
        assertThat(userRepository.findByPhoneNumberAndDeletedAtIsNull("+233244111111"))
                .isPresent()
                .get()
                .extracting(User::getId)
                .isEqualTo(reRegistered.getId());
    }

    @Test
    void liveDuplicatePhoneViolatesThePartialUniqueIndex() {
        userRepository.saveAndFlush(user("+233244222222"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user("+233244222222")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_users_phone");
    }

    @Test
    void optimisticLockingColumnIsManagedByHibernate() {
        User saved = userRepository.saveAndFlush(user("+233244333333"));
        long initialVersion = saved.getVersion();

        saved.setFullName("Kofi B. Updated");
        User updated = userRepository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isGreaterThan(initialVersion);
    }
}
