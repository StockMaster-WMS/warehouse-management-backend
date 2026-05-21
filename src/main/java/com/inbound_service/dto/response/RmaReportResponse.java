package com.inbound_service.dto.response;

import java.util.List;

public record RmaReportResponse(
        long totalReturns,
        long customerReturns,
        long supplierReturns,
        long pendingApproval,
        long approved,
        long rejected,
        long completed,
        int totalExpectedQty,
        int totalReceivedQty,
        int totalSupplierReturnedQty,
        List<GroupStat> topSuppliers,
        List<GroupStat> topReasons
) {
    public record GroupStat(
            String key,
            String label,
            long documents,
            int quantity
    ) {
    }
}
