package com.auth_service.repository;

import com.auth_service.entity.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
