package com.auth_service.repository;

import com.auth_service.entity.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findByUsername(String username);

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findByUsernameOrEmail(String username, String email);

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findById(UUID id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    @Query("""
            select distinct u
            from UserAccount u
            join u.roles r
            where u.isActive = true and r.code in :roleCodes
            """)
    List<UserAccount> findActiveByRoleCodes(@Param("roleCodes") Collection<String> roleCodes);
}
