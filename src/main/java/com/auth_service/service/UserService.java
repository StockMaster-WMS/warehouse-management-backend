package com.auth_service.service;

import com.auth_service.dto.request.AdminResetPasswordRequest;
import com.auth_service.dto.request.CreateUserRequest;
import com.auth_service.dto.request.UpdateUserRequest;
import com.auth_service.dto.request.UpdateUserRoleRequest;
import com.auth_service.dto.response.UserDetailResponse;
import com.auth_service.dto.response.UserImportErrorResponse;
import com.auth_service.dto.response.UserImportResultResponse;
import com.auth_service.dto.response.UserResponse;
import com.auth_service.dto.response.UserStatisticsResponse;
import com.auth_service.dto.response.RoleResponse;
import com.auth_service.entity.Role;
import com.auth_service.entity.UserAccount;
import com.auth_service.repository.RoleRepository;
import com.auth_service.repository.UserRepository;
import com.common.api.PagedResponse;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.notification.CreateNotificationCommand;
import com.common.notification.NotificationService;
import com.common.notification.NotificationSeverity;
import com.common.notification.NotificationType;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collection;
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
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final WarehouseRepository warehouseRepository;

    public PagedResponse<UserResponse> findUsers(Pageable pageable, String keyword, String role, Boolean active) {
        Specification<UserAccount> spec = userSpecification(keyword, role, active);
        Page<UserResponse> page = userRepository.findAll(spec, pageable).map(this::toUserResponse);
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    public List<UserResponse> getActiveWarehouseStaff(UUID warehouseId, Collection<UUID> visibleWarehouseIds) {
        if (warehouseId != null && !warehouseRepository.existsById(warehouseId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho");
        }
        if (visibleWarehouseIds != null) {
            if (visibleWarehouseIds.isEmpty()) {
                return List.of();
            }
            if (warehouseId != null && !visibleWarehouseIds.contains(warehouseId)) {
                throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được phân quyền thao tác kho này");
            }
            if (warehouseId == null) {
                return userRepository.findActiveWarehouseStaffByWarehouseIds(visibleWarehouseIds).stream()
                        .map(this::toUserResponse)
                        .collect(Collectors.toList());
            }
        }
        return userRepository.findActiveWarehouseStaffByWarehouseId(warehouseId).stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(UUID id) {
        return toUserResponse(getUser(id));
    }

    public UserDetailResponse getUserDetail(UUID id) {
        UserAccount user = getUser(id);
        UserResponse response = toUserResponse(user);
        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("active", Boolean.TRUE.equals(user.getIsActive()));
        statistics.put("rolesCount", user.getRoles() == null ? 0 : user.getRoles().size());
        statistics.put("createdAt", user.getCreatedAt());
        var recentLogs = auditLogService.findRecentForEntity("USER", id);
        statistics.put("recentAuditCount", recentLogs.size());
        return new UserDetailResponse(response, statistics, recentLogs);
    }

    public UserStatisticsResponse getStatistics() {
        long total = userRepository.count();
        long active = userRepository.countByIsActive(true);
        long inactive = userRepository.countByIsActive(false);
        long admins = userRepository.countByRoleCode("ADMIN");
        return new UserStatisticsResponse(total, active, inactive, admins);
    }

    public List<RoleResponse> getRoles() {
        return roleRepository.findAll().stream()
                .map(role -> new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription()))
                .toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByUsername(username)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Username đã tồn tại");
        }
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Email đã tồn tại");
        }

        UserAccount user = UserAccount.builder()
                .username(username)
                .email(email)
                .fullName(normalizeFullName(request.fullName()))
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles(resolveRoles(request.roles()))
                .warehouses(resolveWarehouses(request.warehouseIds()))
                .isActive(true)
                .build();

        UserAccount saved = userRepository.save(user);
        UserResponse response = toUserResponse(saved);
        auditLogService.record("USER", "CREATE", "Tạo người dùng",
                "USER", saved.getId(), saved.getUsername(), null, response, null, userMetadata(saved));
        return response;
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request, UUID actorId) {
        UserAccount user = getUser(userId);
        UserResponse before = toUserResponse(user);

        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        userRepository.findByUsername(username)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Username đã tồn tại");
                });
        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Email đã tồn tại");
                });

        Set<Role> roles = resolveRoles(request.roles());
        if (isSelf(userId, actorId) && !hasRole(roles, "ADMIN")) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không được tự gỡ quyền ADMIN của chính mình");
        }
        if (isSelf(userId, actorId) && Boolean.FALSE.equals(request.isActive())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không được tự khóa tài khoản của chính mình");
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(normalizeFullName(request.fullName()));
        user.setRoles(roles);
        user.setWarehouses(resolveWarehouses(request.warehouseIds()));
        if (request.isActive() != null) {
            user.setIsActive(request.isActive());
        }

        UserAccount saved = userRepository.save(user);
        UserResponse response = toUserResponse(saved);
        auditLogService.record("USER", "UPDATE", "Cập nhật người dùng",
                "USER", saved.getId(), saved.getUsername(), before, response, null, userMetadata(saved));
        return response;
    }

    @Transactional
    public UserResponse updateRoles(UUID userId, UpdateUserRoleRequest request, UUID actorId) {
        UserAccount user = getUser(userId);
        UserResponse before = toUserResponse(user);
        Set<Role> roles = resolveRoles(request.roles());
        if (isSelf(userId, actorId) && !hasRole(roles, "ADMIN")) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không được tự gỡ quyền ADMIN của chính mình");
        }
        user.setRoles(roles);
        UserResponse response = toUserResponse(userRepository.save(user));
        notifyRoleChanged(user, response);
        auditLogService.record("USER", "ROLE_CHANGE", "Cập nhật vai trò người dùng",
                "USER", user.getId(), user.getUsername(), before, response, null, userMetadata(user));
        return response;
    }

    @Transactional
    public UserResponse toggleStatus(UUID userId, UUID actorId) {
        UserAccount user = getUser(userId);
        if (isSelf(userId, actorId)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không được tự khóa hoặc mở khóa tài khoản của chính mình");
        }
        UserResponse before = toUserResponse(user);
        user.setIsActive(!Boolean.TRUE.equals(user.getIsActive()));
        UserResponse response = toUserResponse(userRepository.save(user));
        notifyStatusChanged(user, response);
        auditLogService.record("USER", "STATUS_CHANGE", "Cập nhật trạng thái người dùng",
                "USER", user.getId(), user.getUsername(), before, response, null, userMetadata(user));
        return response;
    }

    @Transactional
    public UserResponse resetPassword(UUID userId, AdminResetPasswordRequest request) {
        UserAccount user = getUser(userId);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        UserAccount saved = userRepository.save(user);
        UserResponse response = toUserResponse(saved);
        notificationService.create(new CreateNotificationCommand(
                saved.getId(),
                NotificationType.SYSTEM_ALERT,
                NotificationSeverity.WARNING,
                "Mật khẩu tài khoản đã được đặt lại",
                "Quản trị viên vừa đặt lại mật khẩu tài khoản của bạn.",
                "USER",
                saved.getId()));
        auditLogService.record("USER", "RESET_PASSWORD", "Đặt lại mật khẩu người dùng",
                "USER", saved.getId(), saved.getUsername(), null, Map.of("userId", saved.getId()), null, userMetadata(saved));
        return response;
    }

    public UserImportResultResponse previewImport(MultipartFile file) {
        return importUsers(file, true);
    }

    @Transactional
    public UserImportResultResponse importUsers(MultipartFile file) {
        return importUsers(file, false);
    }

    private UserImportResultResponse importUsers(MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File import không được để trống");
        }
        List<ImportRow> rows = readImportRows(file);
        List<UserResponse> users = new ArrayList<>();
        List<UserImportErrorResponse> errors = new ArrayList<>();
        Set<String> usernamesInFile = new LinkedHashSet<>();
        Set<String> emailsInFile = new LinkedHashSet<>();

        for (ImportRow row : rows) {
            try {
                validateImportRow(row, usernamesInFile, emailsInFile);
                UserResponse response;
                if (dryRun) {
                    response = new UserResponse(null, row.username(), row.email(), row.fullName(),
                            String.join(",", row.roles()), row.active(), null, List.of(), List.of());
                } else {
                    UserAccount user = UserAccount.builder()
                            .username(row.username())
                            .email(row.email())
                            .fullName(row.fullName())
                            .passwordHash(passwordEncoder.encode(row.password()))
                            .roles(resolveRoles(row.roles()))
                            .isActive(row.active())
                            .build();
                    UserAccount saved = userRepository.save(user);
                    response = toUserResponse(saved);
                    auditLogService.record("USER", "CREATE", "Import người dùng từ Excel",
                            "USER", saved.getId(), saved.getUsername(), null, response, null, userMetadata(saved));
                }
                users.add(response);
            } catch (AppException ex) {
                errors.add(new UserImportErrorResponse(row.rowNumber(), row.username(), row.email(), ex.getMessage()));
            }
        }

        if (!dryRun) {
            auditLogService.record("USER", "IMPORT", "Import Excel người dùng",
                    "USER_IMPORT", null, file.getOriginalFilename(), null,
                    Map.of("successCount", users.size(), "failedCount", errors.size()),
                    null, Map.of("fileName", file.getOriginalFilename(), "totalRows", rows.size()));
        }
        return new UserImportResultResponse(rows.size(), users.size(), errors.size(), users, errors);
    }

    private Specification<UserAccount> userSpecification(String keyword, String role, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                query.distinct(true);
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("fullName")), like)));
            }
            if (StringUtils.hasText(role) && !"all".equalsIgnoreCase(role.trim())) {
                Join<UserAccount, Role> roleJoin = root.join("roles", JoinType.INNER);
                predicates.add(cb.equal(cb.upper(roleJoin.get("code")), role.trim().toUpperCase(Locale.ROOT)));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("isActive"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<ImportRow> readImportRows(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() <= 1) {
                throw new AppException(ErrorCode.BAD_REQUEST, "File Excel không có dữ liệu người dùng");
            }
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> headers = readHeaders(sheet.getRow(0), formatter);
            List<ImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                rows.add(new ImportRow(
                        i + 1,
                        normalizeUsername(readCell(row, headers, formatter, "username")),
                        normalizeEmail(readCell(row, headers, formatter, "email")),
                        normalizeFullName(readCell(row, headers, formatter, "fullName")),
                        readCell(row, headers, formatter, "password"),
                        parseRoles(readCell(row, headers, formatter, "roles")),
                        parseActive(readCell(row, headers, formatter, "isActive"))));
            }
            return rows;
        } catch (IOException ex) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không đọc được file Excel");
        }
    }

    private Map<String, Integer> readHeaders(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File Excel thiếu dòng tiêu đề");
        }
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String key = canonicalHeader(formatter.formatCellValue(cell));
            if (StringUtils.hasText(key)) {
                headers.put(key, cell.getColumnIndex());
            }
        }
        for (String required : List.of("username", "email", "fullName", "password", "roles")) {
            if (!headers.containsKey(required)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "File Excel thiếu cột bắt buộc: " + required);
            }
        }
        return headers;
    }

    private String readCell(Row row, Map<String, Integer> headers, DataFormatter formatter, String key) {
        Integer index = headers.get(key);
        return index == null ? "" : formatter.formatCellValue(row.getCell(index)).trim();
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (StringUtils.hasText(formatter.formatCellValue(cell))) {
                return false;
            }
        }
        return true;
    }

    private void validateImportRow(ImportRow row, Set<String> usernamesInFile, Set<String> emailsInFile) {
        if (!StringUtils.hasText(row.username()) || row.username().length() < 3 || row.username().length() > 50) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Username phải từ 3 đến 50 ký tự");
        }
        if (!StringUtils.hasText(row.email()) || !row.email().contains("@")) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Email không hợp lệ");
        }
        if (!StringUtils.hasText(row.fullName())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Họ tên không được để trống");
        }
        if (!StringUtils.hasText(row.password()) || row.password().length() < 6) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mật khẩu phải có ít nhất 6 ký tự");
        }
        if (row.roles().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cần gán ít nhất một vai trò");
        }
        if (!usernamesInFile.add(row.username().toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Username bị trùng trong file");
        }
        if (!emailsInFile.add(row.email().toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Email bị trùng trong file");
        }
        if (userRepository.existsByUsername(row.username())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Username đã tồn tại");
        }
        if (userRepository.existsByEmail(row.email())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Email đã tồn tại");
        }
        resolveRoles(row.roles());
    }

    private UserAccount getUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private Set<Role> resolveRoles(Set<String> rawRoles) {
        return rawRoles.stream()
                .map(this::normalizeRoleCode)
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vai trò: " + code)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Warehouse> resolveWarehouses(Set<UUID> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Warehouse> warehouses = new LinkedHashSet<>();
        for (UUID warehouseId : warehouseIds) {
            Warehouse warehouse = warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                            "Không tìm thấy kho: " + warehouseId));
            if (!Boolean.TRUE.equals(warehouse.getIsActive())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Kho đã ngừng hoạt động: " + warehouse.getCode());
            }
            warehouses.add(warehouse);
        }
        return warehouses;
    }

    private Set<String> parseRoles(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split("[,;|]"))
                .map(this::normalizeRoleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String canonicalHeader(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace(" ", "");
        return switch (normalized) {
            case "username", "tendangnhap" -> "username";
            case "email" -> "email";
            case "fullname", "hoten", "name" -> "fullName";
            case "password", "matkhau" -> "password";
            case "roles", "role", "vaitro" -> "roles";
            case "isactive", "active", "trangthai", "status" -> "isActive";
            default -> normalized;
        };
    }

    private Boolean parseActive(String raw) {
        if (!StringUtils.hasText(raw)) {
            return true;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return !Set.of("false", "0", "no", "inactive", "locked", "disabled", "khoa", "khóa").contains(value);
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFullName(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeRoleCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isSelf(UUID userId, UUID actorId) {
        return actorId != null && actorId.equals(userId);
    }

    private boolean hasRole(Set<Role> roles, String code) {
        return roles.stream().anyMatch(role -> code.equalsIgnoreCase(role.getCode()));
    }

    private void notifyRoleChanged(UserAccount user, UserResponse response) {
        notificationService.create(new CreateNotificationCommand(
                user.getId(),
                NotificationType.ROLE_CHANGED,
                NotificationSeverity.WARNING,
                "Quyền tài khoản đã thay đổi",
                "Vai trò hiện tại của bạn: " + response.roles(),
                "USER",
                user.getId()));
    }

    private void notifyStatusChanged(UserAccount user, UserResponse response) {
        notificationService.create(new CreateNotificationCommand(
                user.getId(),
                NotificationType.SYSTEM_ALERT,
                NotificationSeverity.WARNING,
                "Trạng thái tài khoản đã thay đổi",
                Boolean.TRUE.equals(response.isActive())
                        ? "Tài khoản của bạn đã được kích hoạt"
                        : "Tài khoản của bạn đã bị tạm khóa",
                "USER",
                user.getId()));
    }

    private Map<String, Object> userMetadata(UserAccount user) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("username", user.getUsername());
        metadata.put("email", user.getEmail());
        metadata.put("fullName", user.getFullName());
        metadata.put("roles", user.getRoleCodesCsv());
        metadata.put("warehouseIds", user.getWarehouses() == null ? List.of()
                : user.getWarehouses().stream().map(Warehouse::getId).toList());
        metadata.put("isActive", user.getIsActive());
        return metadata;
    }

    private UserResponse toUserResponse(UserAccount user) {
        List<Warehouse> warehouses = user.getWarehouses() == null
                ? List.of()
                : user.getWarehouses().stream()
                        .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getCode(), right.getCode()))
                        .toList();
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRoleCodesCsv(),
                user.getIsActive(),
                user.getCreatedAt(),
                warehouses.stream().map(Warehouse::getId).toList(),
                warehouses.stream()
                        .map(warehouse -> warehouse.getCode() == null
                                ? warehouse.getName()
                                : warehouse.getName() + " (" + warehouse.getCode() + ")")
                        .toList()
        );
    }

    private record ImportRow(
            int rowNumber,
            String username,
            String email,
            String fullName,
            String password,
            Set<String> roles,
            Boolean active
    ) {
    }
}
