package com.warehouse_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.common.audit.AuditLogResponse;
import com.common.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks/audit-logs")
public class StockAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ApiResponse<PagedResponse<AuditLogResponse>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success("Lấy nhật ký tồn kho thành công",
                auditLogService.findAll(pageable, "STOCK", actionType, entityType, keyword));
    }
}
