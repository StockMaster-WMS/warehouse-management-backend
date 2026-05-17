package com.warehouse_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecordCountRequest(
    @NotNull(message = "ID dòng kiểm kê không được để trống")
    UUID itemId,

    @NotNull(message = "Số lượng đếm được không được để trống")
    @Min(value = 0, message = "Số lượng đếm được không được âm")
    Integer countedQty,

    String notes
) {}
