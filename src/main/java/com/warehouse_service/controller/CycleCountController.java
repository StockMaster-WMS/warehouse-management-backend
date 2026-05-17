package com.warehouse_service.controller;

import com.auth_service.entity.UserAccount;
import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.warehouse_service.dto.request.CreateCycleCountRequest;
import com.warehouse_service.dto.request.RecordCountRequest;
import com.warehouse_service.dto.response.CycleCountResponse;
import com.warehouse_service.service.CycleCountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cycle-counts")
@Tag(name = "Cycle Count", description = "Quản lý kiểm kê kho")
@SecurityRequirement(name = "bearerAuth")
public class CycleCountController {

    private final CycleCountService cycleCountService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy danh sách đợt kiểm kê (phân trang)")
    public ApiResponse<PagedResponse<CycleCountResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));
        return ApiResponse.success("Lấy danh sách kiểm kê thành công",
                cycleCountService.getAll(pageable, keyword, status, warehouseId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy chi tiết một đợt kiểm kê")
    public ApiResponse<CycleCountResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy thông tin kiểm kê thành công", cycleCountService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Tạo đợt kiểm kê mới")
    public ApiResponse<CycleCountResponse> create(@Valid @RequestBody CreateCycleCountRequest request) {
        UUID userId = resolveCurrentUserId();
        return ApiResponse.success("Tạo đợt kiểm kê thành công",
                cycleCountService.create(request, userId));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Bắt đầu thực hiện kiểm kê (PENDING → IN_PROGRESS)")
    public ApiResponse<CycleCountResponse> start(@PathVariable UUID id) {
        return ApiResponse.success("Bắt đầu kiểm kê thành công", cycleCountService.startCounting(id));
    }

    @PostMapping("/{id}/record")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Ghi nhận số lượng đếm được (batch)")
    public ApiResponse<CycleCountResponse> record(@PathVariable UUID id,
                                                   @Valid @RequestBody RecordCountRequest request) {
        return ApiResponse.success("Ghi nhận số lượng thành công", cycleCountService.recordCount(id, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Nộp kết quả kiểm kê để chờ duyệt (IN_PROGRESS → COMPLETED)")
    public ApiResponse<CycleCountResponse> complete(@PathVariable UUID id) {
        return ApiResponse.success("Đã nộp kết quả kiểm kê, chờ duyệt",
                cycleCountService.submitForReview(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Duyệt kiểm kê và điều chỉnh tồn kho (COMPLETED → APPROVED)")
    public ApiResponse<CycleCountResponse> approve(@PathVariable UUID id) {
        UUID userId = resolveCurrentUserId();
        return ApiResponse.success("Đã duyệt và điều chỉnh tồn kho thành công",
                cycleCountService.approveAndAdjust(id, userId));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Huỷ đợt kiểm kê (PENDING/IN_PROGRESS → CANCELLED)")
    public ApiResponse<CycleCountResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.success("Đã huỷ đợt kiểm kê", cycleCountService.cancel(id));
    }

    // ─── Utility ─────────────────────────────────────────────────────────────

    private UUID resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserAccount user) {
            return user.getId();
        }
        return null;
    }
}
