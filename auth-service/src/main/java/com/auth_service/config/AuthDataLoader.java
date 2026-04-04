package com.auth_service.config;

import com.auth_service.entity.Role;
import com.auth_service.entity.UserAccount;
import com.auth_service.repository.RoleRepository;
import com.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthDataLoader {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadInitData() {
        Map<String, RoleSeed> roleSeeds = roleSeeds();
        roleSeeds.forEach((code, seed) -> ensureRole(seed));

        ensureUser("admin", "admin@warehouse.local", "Admin@12345", roleRepository.findByCode("ADMIN").orElseThrow());
        ensureUser("manager", "manager@warehouse.local", "Manager@12345", roleRepository.findByCode("WAREHOUSE_MANAGER").orElseThrow());
        ensureUser("staff", "staff@warehouse.local", "Staff@12345", roleRepository.findByCode("WAREHOUSE_STAFF").orElseThrow());
        ensureUser("report", "report@warehouse.local", "Report@12345", roleRepository.findByCode("REPORT_VIEWER").orElseThrow());
    }

    private void ensureRole(RoleSeed seed) {
        roleRepository.findByCode(seed.code()).ifPresentOrElse(existing -> {
            boolean updated = false;
            if (!seed.name().equals(existing.getName())) {
                existing.setName(seed.name());
                updated = true;
            }
            if (!seed.description().equals(existing.getDescription())) {
                existing.setDescription(seed.description());
                updated = true;
            }
            if (updated) {
                roleRepository.save(existing);
            }
        }, () -> roleRepository.save(Role.builder()
                .code(seed.code())
                .name(seed.name())
                .description(seed.description())
                .build()));
    }

    private void ensureUser(String username, String email, String rawPassword, Role role) {
        userRepository.findByUsername(username).ifPresentOrElse(existing -> {
            boolean changed = false;
            if (!email.equalsIgnoreCase(existing.getEmail())) {
                existing.setEmail(email);
                changed = true;
            }
            if (!Boolean.TRUE.equals(existing.getIsActive())) {
                existing.setIsActive(true);
                changed = true;
            }
            if (existing.getRoles() == null) {
                existing.setRoles(new LinkedHashSet<>());
            }
            if (existing.getRoles().add(role)) {
                changed = true;
            }
            if (changed) {
                userRepository.save(existing);
            }
        }, () -> {
            UserAccount user = UserAccount.builder()
                    .username(username)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .roles(new LinkedHashSet<>(java.util.List.of(role)))
                    .isActive(true)
                    .build();
            userRepository.save(user);
        });
    }

    private Map<String, RoleSeed> roleSeeds() {
        Map<String, RoleSeed> seeds = new LinkedHashMap<>();
        seeds.put("USER", new RoleSeed("USER", "Người dùng", "Tài khoản mặc định của hệ thống"));
        seeds.put("ADMIN", new RoleSeed("ADMIN", "Quản trị hệ thống", "Toàn quyền quản trị hệ thống"));
        seeds.put("WAREHOUSE_MANAGER", new RoleSeed("WAREHOUSE_MANAGER", "Quản lý kho", "Điều phối và giám sát nghiệp vụ kho"));
        seeds.put("WAREHOUSE_STAFF", new RoleSeed("WAREHOUSE_STAFF", "Nhân viên kho", "Thực hiện nhập, xuất, kiểm kê và xử lý vị trí"));
        seeds.put("REPORT_VIEWER", new RoleSeed("REPORT_VIEWER", "Người xem báo cáo", "Chỉ xem dashboard và báo cáo"));
        return seeds;
    }

    private record RoleSeed(String code, String name, String description) {
    }
}