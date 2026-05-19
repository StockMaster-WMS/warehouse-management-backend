package com.common.dashboard;

import com.common.api.ApiResponse;
import com.common.dashboard.dto.DashboardSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "API tổng hợp dữ liệu cho trang dashboard")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'REPORT_VIEWER')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "Tổng quan dashboard", description = "Trả về thẻ số liệu, biểu đồ xuất/nhập và thông báo vận hành")
    public ApiResponse<DashboardSummaryResponse> getSummary() {
        return ApiResponse.success("Lấy tổng quan dashboard thành công", dashboardService.getSummary());
    }
}
