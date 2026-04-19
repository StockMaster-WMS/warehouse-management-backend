package com.warehouse_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.warehouse_service.dto.response.StockMovementResponse;
import com.warehouse_service.service.StockMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks/movements")
@Tag(name = "Stock Movement ", description = "Lịch sử biến động tồn kho")
public class StockMovementController {

        private final StockMovementService stockMovementService;

        @GetMapping
        @Operation(summary = "Lịch sử biến động tồn kho", description = "Phân trang; lọc theo kho, vị trí, sản phẩm, loại biến động, khoảng thời gian")
        public ApiResponse<PagedResponse<StockMovementResponse>> getAll(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        @RequestParam(defaultValue = "createdAt") String sort,
                        @RequestParam(defaultValue = "desc") String sortDir,
                        @Parameter(description = "ID kho") @RequestParam(required = false) UUID warehouseId,
                        @Parameter(description = "ID vị trí") @RequestParam(required = false) UUID locationId,
                        @Parameter(description = "ID sản phẩm") @RequestParam(required = false) UUID productId,
                        @Parameter(description = "Loại biến động: INBOUND, OUTBOUND, ADJUSTMENT, RESERVE, RELEASE") @RequestParam(required = false) String movementType,
                        @Parameter(description = "Từ ngày (ISO 8601)") @RequestParam(required = false) OffsetDateTime from,
                        @Parameter(description = "Đến ngày (ISO 8601)") @RequestParam(required = false) OffsetDateTime to) {

                Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
                PagedResponse<StockMovementResponse> data = stockMovementService.findAll(
                                pageable, warehouseId, locationId, productId, movementType, from, to);
                return ApiResponse.success("Lấy lịch sử biến động tồn kho thành công", data);
        }
}
