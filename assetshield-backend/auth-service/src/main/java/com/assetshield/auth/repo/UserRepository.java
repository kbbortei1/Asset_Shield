package com.assetshield.auth.repo;

import com.assetshield.auth.domain.Role;
import com.assetshield.auth.domain.User;
import com.assetshield.auth.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumberAndDeletedAtIsNull(String phoneNumber);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByRoleAndStatusAndDeletedAtIsNull(Role role, UserStatus status);
}
