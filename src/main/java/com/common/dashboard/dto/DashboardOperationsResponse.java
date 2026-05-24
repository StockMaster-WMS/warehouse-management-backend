package com.common.dashboard.dto;

public record DashboardOperationsResponse(
        long pendingPickingOrders,
        long overduePickingTasks,
        long lowStockItems,
        long nearExpiryLots,
        double cycleCountAccuracy,
        long completedOrdersToday,
        long outboundOrdersWithoutPicking,
        long largeVarianceCycleCountItems
) {
}
