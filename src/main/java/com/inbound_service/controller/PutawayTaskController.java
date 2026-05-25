package com.inbound_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.inbound_service.dto.request.CompletePutawayRequest;
import com.inbound_service.dto.request.UpdatePutawayTaskRequest;
import com.inbound_service.dto.response.PutawayLocationSuggestionResponse;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.service.PutawayTaskService;
import com.warehouse_service.service.WarehouseAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/putaway-tasks")
@Tag(name = "Putaway", description = "Nhiệm vụ đưa hàng vào vị trí sau khi nhận PO")
public class PutawayTaskController {

    private final PutawayTaskService putawayTaskService;
    private final WarehouseAccessService warehouseAccessService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Danh sách putaway", description = "Phân trang; lọc poItemId và/hoặc status")
    public ApiResponse<PagedResponse<PutawayTaskResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "ID dòng PO") @RequestParam(required = false) UUID poItemId,
            @Parameter(description = "PENDING | IN_PROGRESS | COMPLETED | CANCELLED") @RequestParam(required = false) String status,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        return ApiResponse.success("Lấy danh sách putaway thành công",
                putawayTaskService.findAll(pageable, poItemId, status,
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Chi tiết putaway")
    public ApiResponse<PutawayTaskResponse> getById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Lấy putaway thành công",
                putawayTaskService.findById(id, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @GetMapping("/{id}/location-suggestions")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Goi y vi tri xep hang", description = "Tra vi tri hien tai, vi tri cu cua san pham va vi tri trong de FE chon khi sua putaway")
    public ApiResponse<List<PutawayLocationSuggestionResponse>> suggestLocations(@PathVariable UUID id,
            @RequestParam(defaultValue = "30") int limit,
            Authentication authentication) {
        return ApiResponse.success("Lay danh sach vi tri xep hang thanh cong",
                putawayTaskService.suggestLocations(id, limit,
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Cập nhật putaway", description = "Gợi ý vị trí, trạng thái (PENDING/IN_PROGRESS/CANCELLED)")
    public ApiResponse<PutawayTaskResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdatePutawayTaskRequest request,
            Authentication authentication) {
        return ApiResponse.success("Cập nhật putaway thành công",
                putawayTaskService.update(id, request, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Hoàn tất putaway", description = "Ghi nhận vị trí thực tế trong kho")
    public ApiResponse<PutawayTaskResponse> complete(@PathVariable UUID id,
            @Valid @RequestBody CompletePutawayRequest request,
            Authentication authentication) {
        return ApiResponse.success("Hoàn tất putaway thành công",
                putawayTaskService.complete(id, request, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }
}
