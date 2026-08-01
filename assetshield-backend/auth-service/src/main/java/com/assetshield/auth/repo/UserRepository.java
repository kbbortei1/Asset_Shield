package com.assetshield.auth.repo;

import com.assetshield.auth.domain.Role;
import com.assetshield.auth.domain.User;
import com.assetshield.auth.domain.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumberAndDeletedAtIsNull(String phoneNumber);

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByRoleAndStatusAndDeletedAtIsNull(Role role, UserStatus status);

    // ── admin broadcast audiences ────────────────────────────────────────────

    long countByStatusAndRoleInAndDeletedAtIsNull(UserStatus status, Collection<Role> roles);

    /** All recipient ids for a segment broadcast. */
    @Query("""
            select u.id from User u
            where u.status = :status and u.role in :roles and u.deletedAt is null
            """)
    List<UUID> idsByStatusAndRoles(@Param("status") UserStatus status,
                                   @Param("roles") Collection<Role> roles);

    /** Directory search for the "specific people" picker (q = "" matches all). */
    @Query("""
            select u from User u
            where u.status = :status and u.role in :roles and u.deletedAt is null
              and (lower(u.fullName) like lower(concat('%', :q, '%'))
                   or u.phoneNumber like concat('%', :q, '%'))
            order by u.fullName asc
            """)
    Page<User> searchDirectory(@Param("status") UserStatus status,
                               @Param("roles") Collection<Role> roles,
                               @Param("q") String q, Pageable pageable);
}
