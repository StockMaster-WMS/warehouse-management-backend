package com.inbound_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PoItemImportResponse(
        @Schema(description = "Số dòng dữ liệu đã đọc (không tính header)")
        int totalRows,

        @Schema(description = "Số dòng import thành công (sản phẩm tạo + dòng PO thêm)")
        int successCount,

        @Schema(description = "Số dòng lỗi")
        int failureCount,

        @Schema(description = "Danh sách dòng PO vừa tạo thành công")
        List<PoItemResponse> createdItems,

        @Schema(description = "Chi tiết lỗi theo dòng (tối đa 100)")
        List<ImportRowError> errors
) {
    public record ImportRowError(
            @Schema(description = "Số dòng trên file Excel (bắt đầu từ 1)")
            int rowNumber,
            @Schema(description = "Thông báo lỗi")
            String message
    ) {
    }
}
