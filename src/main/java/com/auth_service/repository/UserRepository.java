package com.auth_service.repository;

import com.auth_service.entity.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {
        @EntityGraph(attributePaths = "roles")
        Optional<UserAccount> findByUsername(String username);

        @EntityGraph(attributePaths = { "roles", "warehouses" })
        Optional<UserAccount> findByEmail(String email);

        @EntityGraph(attributePaths = "roles")
        Optional<UserAccount> findByUsernameOrEmail(String username, String email);

        @EntityGraph(attributePaths = { "roles", "warehouses" })
        Optional<UserAccount> findById(UUID id);

        boolean existsByUsername(String username);

        boolean existsByEmail(String email);

        @Override
        @EntityGraph(attributePaths = { "roles", "warehouses" })
        Page<UserAccount> findAll(Specification<UserAccount> spec, Pageable pageable);

        @EntityGraph(attributePaths = { "roles", "warehouses" })
        List<UserAccount> findByUsernameInOrEmailIn(Collection<String> usernames, Collection<String> emails);

        long countByIsActive(Boolean isActive);

        @Query("""
                        select count(distinct u)
                        from UserAccount u
                        join u.roles r
                        where r.code = :roleCode
                        """)
        long countByRoleCode(@Param("roleCode") String roleCode);

        @EntityGraph(attributePaths = { "roles", "warehouses" })
        @Query("""
                        select distinct u
                        from UserAccount u
                        join u.roles r
                        where u.isActive = true and r.code in :roleCodes
                        """)
        List<UserAccount> findActiveByRoleCodes(@Param("roleCodes") Collection<String> roleCodes);

        @EntityGraph(attributePaths = { "roles", "warehouses" })
        @Query("""
                        select distinct u
                        from UserAccount u
                        join u.roles r
                        left join u.warehouses w
                        where u.isActive = true
                          and r.code = 'WAREHOUSE_STAFF'
                          and (:warehouseId is null or w.id = :warehouseId)
                        """)
        List<UserAccount> findActiveWarehouseStaffByWarehouseId(@Param("warehouseId") UUID warehouseId);

        @EntityGraph(attributePaths = { "roles", "warehouses" })
        @Query("""
                        select distinct u
                        from UserAccount u
                        join u.roles r
                        join u.warehouses w
                        where u.isActive = true
                          and r.code = 'WAREHOUSE_STAFF'
                          and w.id in :warehouseIds
                        """)
        List<UserAccount> findActiveWarehouseStaffByWarehouseIds(@Param("warehouseIds") Collection<UUID> warehouseIds);

        @Query("""
                        select count(distinct u) > 0
                        from UserAccount u
                        join u.roles r
                        join u.warehouses w
                        where u.id = :userId
                          and u.isActive = true
                          and r.code = 'WAREHOUSE_STAFF'
                          and w.id = :warehouseId
                        """)
        boolean existsActiveWarehouseStaffInWarehouse(@Param("userId") UUID userId,
                        @Param("warehouseId") UUID warehouseId);

        @Query("""
                        select distinct w.id
                        from UserAccount u
                        join u.warehouses w
                        where u.id = :userId
                        """)
        List<UUID> findWarehouseIdsByUserId(@Param("userId") UUID userId);
}
