package com.auth_service.service;

import com.auth_service.dto.request.CreateUserRequest;
import com.auth_service.dto.request.UpdateUserRoleRequest;
import com.auth_service.dto.response.UserResponse;
import com.auth_service.entity.Role;
import com.auth_service.entity.UserAccount;
import com.auth_service.repository.RoleRepository;
import com.auth_service.repository.UserRepository;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(UUID id) {
        return userRepository.findById(id)
                .map(this::toUserResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng"));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Username đã tồn tại");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Email đã tồn tại");
        }

        Set<Role> roles = request.roles().stream()
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vai trò: " + code)))
                .collect(Collectors.toSet());

        UserAccount user = UserAccount.builder()
                .username(request.username().trim())
                .email(request.email().trim().toLowerCase())
                .fullName(request.fullName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles(roles)
                .isActive(true)
                .build();

        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateRoles(UUID userId, UpdateUserRoleRequest request) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng"));

        Set<Role> roles = request.roles().stream()
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vai trò: " + code)))
                .collect(Collectors.toSet());

        user.setRoles(roles);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse toggleStatus(UUID userId) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng"));

        user.setIsActive(!Boolean.TRUE.equals(user.getIsActive()));
        return toUserResponse(userRepository.save(user));
    }

    private UserResponse toUserResponse(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRoleCodesCsv(),
                user.getIsActive(),
                user.getCreatedAt()
        );
    }
}
