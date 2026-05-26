package com.inbound_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.inbound_service.dto.request.AddPurchaseOrderItemRequest;
import com.inbound_service.dto.request.CreatePurchaseOrderRequest;
import com.inbound_service.dto.request.UpdatePurchaseOrderRequest;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.dto.response.PurchaseOrderDetailResponse;
import com.inbound_service.dto.response.PurchaseOrderResponse;
import com.inbound_service.service.PoItemService;
import com.inbound_service.service.PurchaseOrderService;
import com.warehouse_service.service.WarehouseAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/purchase-orders")
@Tag(name = "Purchase Order ", description = "Quản lý đơn nhập hàng")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final PoItemService poItemService;
    private final WarehouseAccessService warehouseAccessService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy danh sách đơn nhập", description = "Phân trang; lọc keyword, status, supplierId, warehouseId")
    public ApiResponse<PagedResponse<PurchaseOrderResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        return ApiResponse.success("Lấy danh sách đơn nhập thành công",
                purchaseOrderService.findAll(pageable, keyword, status, supplierId, warehouseId, createdFrom, createdTo,
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy đơn nhập theo ID", description = "Trả về chi tiết purchase order theo UUID")
    public ApiResponse<PurchaseOrderResponse> getById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Lấy đơn nhập thành công",
                purchaseOrderService.findById(id, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy chi tiết PO cho màn hình nhập hàng", description = "Trả về PO + danh sách dòng hàng + putaway tasks + tiến độ nhận")
    public ApiResponse<PurchaseOrderDetailResponse> getDetail(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Lấy chi tiết đơn nhập thành công",
                purchaseOrderService.findDetail(id, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @GetMapping("/number/{poNumber}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy đơn nhập theo mã", description = "Tìm purchase order bằng poNumber")
    public ApiResponse<PurchaseOrderResponse> getByPoNumber(@PathVariable String poNumber, Authentication authentication) {
        return ApiResponse.success("Lấy đơn nhập thành công",
                purchaseOrderService.findByPoNumberScoped(poNumber, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Tạo đơn nhập", description = "Tạo mới một purchase order")
    public ApiResponse<PurchaseOrderResponse> create(@Valid @RequestBody CreatePurchaseOrderRequest request,
            Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.warehouseId());
        return ApiResponse.success("Tạo đơn nhập thành công", purchaseOrderService.create(request));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Them dong hang vao don nhap", description = "Cho phep bo sung dong hang khi PO con o trang thai DRAFT; lineNumber co the bo trong de backend tu tang")
    public ApiResponse<PoItemResponse> addItem(@PathVariable UUID id,
            @Valid @RequestBody AddPurchaseOrderItemRequest request,
            Authentication authentication) {
        return ApiResponse.success("Them dong hang vao don nhap thanh cong",
                poItemService.addToPurchaseOrder(id, request,
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Duyệt đơn nhập", description = "DRAFT -> APPROVED (cần có ít nhất 1 dòng hàng)")
    public ApiResponse<PurchaseOrderResponse> approve(@PathVariable UUID id) {
        return ApiResponse.success("Duyệt đơn nhập thành công", purchaseOrderService.approve(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Hủy đơn nhập", description = "Cho phép hủy khi đang DRAFT, APPROVED hoặc PARTIAL")
    public ApiResponse<PurchaseOrderResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.success("Hủy đơn nhập thành công", purchaseOrderService.cancel(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Cập nhật đơn nhập", description = "Cập nhật purchase order theo ID")
    public ApiResponse<PurchaseOrderResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdatePurchaseOrderRequest request) {
        return ApiResponse.success("Cập nhật đơn nhập thành công", purchaseOrderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Xóa đơn nhập", description = "Xóa purchase order theo ID")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        purchaseOrderService.delete(id);
        return ApiResponse.success("Xóa đơn nhập thành công", id.toString());
    }

    @GetMapping("/exists-by-supplier/{supplierId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Kiểm tra NCC có đơn nhập", description = "Trả về true nếu supplier đã có ít nhất 1 PO")
    public ApiResponse<Boolean> existsBySupplierId(@PathVariable UUID supplierId) {
        return ApiResponse.success("Kiểm tra thành công",
                purchaseOrderService.existsBySupplierId(supplierId));
    }
}
