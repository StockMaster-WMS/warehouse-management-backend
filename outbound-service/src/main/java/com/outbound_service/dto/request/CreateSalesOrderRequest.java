package com.outbound_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateSalesOrderRequest(
        @NotBlank(message = "Mã đơn xuất không được để trống")
        @Size(max = 30, message = "Mã đơn xuất không được vượt quá 30 ký tự")
        @Schema(description = "Mã đơn xuất (unique)", example = "SO-2026-0001")
        String soNumber,

        @NotBlank(message = "Tên khách hàng không được để trống")
        @Size(max = 200, message = "Tên khách hàng không được vượt quá 200 ký tự")
        @Schema(description = "Tên khách hàng / đơn vị nhận hàng", example = "Công ty TNHH Thương mại ABC")
        String customerName,

        @NotNull(message = "Địa chỉ giao hàng không được để trống")
        @NotEmpty(message = "Địa chỉ giao hàng không được rỗng")
        @Schema(
                description = "Địa chỉ giao hàng (JSON linh hoạt)",
                example = "{\"line1\":\"123 Nguyễn Huệ\",\"ward\":\"Phường Bến Nghé\",\"district\":\"Quận 1\",\"city\":\"TP. Hồ Chí Minh\",\"country\":\"VN\"}")
        Map<String, Object> shippingAddress,

        @NotNull(message = "Mã kho không được để trống")
        @Schema(description = "ID kho (warehouse-service)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID warehouseId,

        @Schema(description = "Độ ưu tiên (mặc định 5 nếu bỏ trống)", example = "5")
        Short priority,

        @Schema(description = "Trạng thái (mặc định PENDING nếu bỏ trống)", example = "PENDING", allowableValues = {
                "PENDING", "PICKING", "PICKED", "PACKED", "SHIPPED" })
        String status
) {
}