package com.warehouse_service.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateCycleCountRequest(
    @NotNull(message = "Warehouse ID không được để trống")
    UUID warehouseId,

    String description,

    OffsetDateTime scheduledAt,

    @NotEmpty(message = "Cần ít nhất một sản phẩm để kiểm kê")
    @Valid
    List<ItemRequest> items
) {
    public record ItemRequest(
        @NotNull UUID productId,
        @NotNull UUID locationId,
        String lotNumber
    ) {}
}
