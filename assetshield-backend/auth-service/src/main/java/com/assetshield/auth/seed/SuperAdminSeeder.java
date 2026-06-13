package com.assetshield.auth.seed;

import com.assetshield.auth.config.AppProperties;
import com.assetshield.auth.domain.Role;
import com.assetshield.auth.domain.User;
import com.assetshield.auth.domain.UserStatus;
import com.assetshield.auth.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent superadmin bootstrap: creates an ACTIVE ADMIN from
 * SUPERADMIN_PHONE / SUPERADMIN_PASSWORD only when no ACTIVE ADMIN exists.
 * The password is BCrypt-hashed and never logged.
 */
@Component
public class SuperAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public SuperAdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRoleAndStatusAndDeletedAtIsNull(Role.ADMIN, UserStatus.ACTIVE)) {
            return;
        }
        String phone = properties.superadmin().phone();
        String password = properties.superadmin().password();
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            log.warn("No ACTIVE admin exists and SUPERADMIN_PHONE/SUPERADMIN_PASSWORD are not set; skipping seed");
            return;
        }
        User admin = new User();
        admin.setPhoneNumber(phone);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFullName("Super Admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);
        log.info("Seeded superadmin account for phone ending in {}",
                phone.substring(Math.max(0, phone.length() - 3)));
    }
}
