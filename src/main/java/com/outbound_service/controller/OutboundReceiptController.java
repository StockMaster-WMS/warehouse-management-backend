package com.outbound_service.controller;

import com.common.api.ApiResponse;
import com.outbound_service.dto.response.OutboundReceiptDetailResponse;
import com.outbound_service.dto.response.OutboundReceiptSummaryResponse;
import com.outbound_service.service.OutboundReceiptService;
import com.warehouse_service.service.WarehouseAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/receipts/out")
@Tag(name = "Outbound Receipts", description = "Tra cứu đơn xuất đã giao để tạo phiếu trả hàng")
@SecurityRequirement(name = "bearerAuth")
public class OutboundReceiptController {

    private final OutboundReceiptService outboundReceiptService;
    private final WarehouseAccessService warehouseAccessService;

    @GetMapping("/by-customer/{customerId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy danh sách đơn xuất đã giao của khách hàng")
    public ApiResponse<List<OutboundReceiptSummaryResponse>> getByCustomer(
            @PathVariable UUID customerId,
            Authentication authentication) {
        return ApiResponse.success("Lấy danh sách đơn xuất của khách hàng thành công",
                outboundReceiptService.findByCustomer(customerId, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy chi tiết đơn xuất để tạo phiếu trả hàng")
    public ApiResponse<OutboundReceiptDetailResponse> getDetails(
            @PathVariable UUID id,
            Authentication authentication) {
        return ApiResponse.success("Lấy chi tiết đơn xuất thành công",
                outboundReceiptService.getDetails(id, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }
}
