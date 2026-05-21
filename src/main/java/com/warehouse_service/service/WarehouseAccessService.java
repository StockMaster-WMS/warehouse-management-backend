package com.warehouse_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.auth_service.repository.UserRepository;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseAccessService {

    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    public boolean canSeeAllWarehouses(Authentication authentication) {
        return hasAuthority(authentication, "ADMIN") || hasAuthority(authentication, "REPORT_VIEWER");
    }

    public boolean isWarehouseManager(Authentication authentication) {
        return hasAuthority(authentication, "WAREHOUSE_MANAGER");
    }

    public boolean isWarehouseStaff(Authentication authentication) {
        return hasAuthority(authentication, "WAREHOUSE_STAFF");
    }

    public UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return UUID.fromString(authentication.getName());
    }

    public List<UUID> visibleWarehouseIds(Authentication authentication) {
        if (canSeeAllWarehouses(authentication)) {
            return null;
        }
        UUID userId = currentUserId(authentication);
        if (userId == null) {
            return List.of();
        }
        if (isWarehouseManager(authentication)) {
            return warehouseRepository.findIdsByManagerId(userId);
        }
        if (isWarehouseStaff(authentication)) {
            return userRepository.findWarehouseIdsByUserId(userId);
        }
        return List.of();
    }

    public void assertCanAccessWarehouse(Authentication authentication, UUID warehouseId) {
        if (warehouseId == null || canSeeAllWarehouses(authentication)) {
            return;
        }
        List<UUID> visibleIds = visibleWarehouseIds(authentication);
        if (CollectionUtils.isEmpty(visibleIds) || !visibleIds.contains(warehouseId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được phân quyền thao tác kho này");
        }
    }

    public UUID scopedWarehouseId(Authentication authentication, UUID requestedWarehouseId) {
        if (canSeeAllWarehouses(authentication)) {
            return requestedWarehouseId;
        }
        List<UUID> visibleIds = visibleWarehouseIds(authentication);
        if (requestedWarehouseId != null) {
            assertCanAccessWarehouse(authentication, requestedWarehouseId);
            return requestedWarehouseId;
        }
        if (visibleIds.size() == 1) {
            return visibleIds.get(0);
        }
        return null;
    }

    public Set<UUID> visibleWarehouseIdSet(Authentication authentication) {
        List<UUID> ids = visibleWarehouseIds(authentication);
        return ids == null ? null : Set.copyOf(ids);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
