package com.ai_service.service;

import com.ai_service.client.AiTextClient;
import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnswerComposerService {

    private static final int MAX_HISTORY_MESSAGES = 4;
    private static final int MAX_TOOL_LIST_ITEMS = 5;
    private static final int MAX_TOOL_TEXT_LENGTH = 2000;
    private final AiTextClient aiTextClient;
    private final ObjectMapper objectMapper;

    // Tạo câu trả lời AI dạng đồng bộ từ route và tool result.
    public String compose(String userMessage, AiIntentResult route, AiToolResult toolResult,
            List<Map<String, String>> history) {
        long start = System.currentTimeMillis();
        String deterministic = deterministicReply(route, toolResult);
        if (deterministic != null) {
            log.info("AI compose mode=deterministic intent={} tool={} outputChars={} durationMs={}",
                    route == null ? "null" : route.getIntent(),
                    toolResult == null ? "null" : toolResult.toolName(),
                    deterministic.length(), System.currentTimeMillis() - start);
            return deterministic;
        }
        log.info("AI compose mode=selected-model start intent={} tool={}",
                route == null ? "null" : route.getIntent(),
                toolResult == null ? "null" : toolResult.toolName());
        String answer = aiTextClient.generateAnswer(buildAnswerPrompt(userMessage, route, toolResult, history));
        log.info("AI compose mode=selected-model done intent={} tool={} outputChars={} durationMs={}",
                route == null ? "null" : route.getIntent(),
                toolResult == null ? "null" : toolResult.toolName(),
                answer == null ? 0 : answer.length(), System.currentTimeMillis() - start);
        return answer;
    }

    // Tạo câu trả lời AI dạng stream từ route và tool result.
        public void composeStream(String userMessage, AiIntentResult route, AiToolResult toolResult,
            List<Map<String, String>> history, Consumer<String> fragmentConsumer, java.util.function.Supplier<Boolean> isCancelled) {
        long start = System.currentTimeMillis();
        if (isCancelled != null && isCancelled.get()) {
            log.info("AI composeStream cancelled before compose intent={} tool={}",
                    route == null ? "null" : route.getIntent(),
                    toolResult == null ? "null" : toolResult.toolName());
            return;
        }
        String deterministic = deterministicReply(route, toolResult);
        if (deterministic != null) {
            log.info("AI composeStream mode=deterministic intent={} tool={} outputChars={} durationMs={}",
                    route == null ? "null" : route.getIntent(),
                    toolResult == null ? "null" : toolResult.toolName(),
                    deterministic.length(), System.currentTimeMillis() - start);
            if (isCancelled == null || !isCancelled.get()) {
                fragmentConsumer.accept(deterministic);
            }
            return;
        }
        if (isCancelled != null && isCancelled.get()) {
            log.info("AI composeStream cancelled before selected-model intent={} tool={}",
                    route == null ? "null" : route.getIntent(),
                    toolResult == null ? "null" : toolResult.toolName());
            return;
        }
        log.info("AI composeStream mode=selected-model start intent={} tool={}",
                route == null ? "null" : route.getIntent(),
                toolResult == null ? "null" : toolResult.toolName());
        aiTextClient.generateAnswerStream(buildAnswerPrompt(userMessage, route, toolResult, history), fragmentConsumer, isCancelled);
        log.info("AI composeStream mode=selected-model done intent={} tool={} cancelled={} durationMs={}",
                route == null ? "null" : route.getIntent(),
                toolResult == null ? "null" : toolResult.toolName(),
                isCancelled != null && isCancelled.get(), System.currentTimeMillis() - start);
    }

    // Trả lời cố định cho các case đơn giản hoặc không có dữ liệu.
    private String deterministicReply(AiIntentResult route, AiToolResult toolResult) {
        if (toolResult == null) {
            return "Tôi chưa xử lý được yêu cầu này. Bạn vui lòng thử lại với câu hỏi cụ thể hơn.";
        }
        if (!toolResult.dataBacked()) {
            return toolResult.message();
        }
        Object data = toolResult.data();
        if (data instanceof List<?> list && list.isEmpty()) {
            return switch (route.getIntent()) {
                case LOW_STOCK -> "Hiện chưa ghi nhận SKU nào dưới mức tồn tối thiểu.";
                case NEAR_EXPIRY -> "Hiện chưa có lô hàng nào sắp hết hạn trong khoảng thời gian này.";
                case STOCK_BY_PRODUCT -> "Tôi chưa tìm thấy tồn kho phù hợp với sản phẩm hoặc SKU bạn hỏi.";
                case STOCK_LOWEST -> "Hiện chưa có dữ liệu tồn kho để xác định sản phẩm thấp nhất.";
                case STOCK_HIGHEST -> "Hiện chưa có dữ liệu tồn kho để xác định sản phẩm cao nhất.";
                case PRODUCT_BY_BARCODE -> "Barcode chưa được đăng ký hoặc không tồn tại.";
                case STOCK_BELOW_THRESHOLD -> "Hiện chưa có sản phẩm nào dưới ngưỡng tồn kho bạn hỏi.";
                case WAREHOUSE_STOCK_SUMMARY -> "Hiện chưa có dữ liệu tồn kho phù hợp với câu hỏi này.";
                case SLOW_MOVING_STOCK -> "Hiện chưa có dữ liệu giao dịch tồn kho để xác định sản phẩm quay vòng chậm.";
                case LOCATION_SEARCH -> "Tôi chưa tìm thấy vị trí kho phù hợp với điều kiện này.";
                case BEST_HEAVY_LOCATION -> "Hiện chưa có vị trí phù hợp cho hàng nặng theo dữ liệu hiện tại.";
                case SUPPLIER_TOP -> "Hiện chưa có dữ liệu đơn nhập phù hợp để xếp hạng nhà cung cấp.";
                case PENDING_PUTAWAY -> "Hiện chưa có task putaway nào đang chờ xử lý.";
                case PUTAWAY_BY_WAREHOUSE -> "Hiện chưa có putaway task đang chờ để tổng hợp theo kho.";
                case INBOUND_TODAY -> "Hôm nay chưa có lô hàng/phiếu nhập kho nào được ghi nhận.";
                case LATEST_INBOUND -> "Hiện chưa có phiếu nhập kho nào trong dữ liệu.";
                case INBOUND_RECEIPT_STATUS, INBOUND_RECEIPT_DETAIL -> "Tôi chưa tìm thấy phiếu nhận hàng phù hợp.";
                case PENDING_PO_RECEIPT -> "Hiện chưa có PO nào đang chờ nhận hàng.";
                case PURCHASE_ORDER_STATUS -> "Tôi chưa tìm thấy đơn nhập phù hợp.";
                case PURCHASE_ORDER_APPROVAL_AUDIT -> "Tôi chưa tìm thấy bản ghi phê duyệt phiếu nhập phù hợp.";
                case OUTBOUND_PRIORITY -> "Hiện chưa có đơn xuất nào trong nhóm cần ưu tiên theo dữ liệu hiện tại.";
                case PACKING_STATUS -> "Hiện chưa có đơn xuất nào đang chờ packing.";
                case PICKING_TOP -> "Hiện chưa có dữ liệu picking để xếp hạng sản phẩm.";
                case PICKING_STATUS -> "Hiện chưa có dòng picking nào đang mở.";
                case ACTIVE_CYCLE_COUNTS -> "Hiện không có lịch kiểm kê cycle count nào đang diễn ra.";
                case CYCLE_COUNT_VARIANCE -> "Hiện chưa ghi nhận dòng kiểm kê đang lệch tồn hoặc đang chờ đếm.";
                case CYCLE_COUNT_STATUS -> "Tôi chưa tìm thấy session kiểm kê phù hợp.";
                case RMA_PENDING -> "Hiện không có yêu cầu trả hàng RMA nào đang chờ xử lý.";
                case NOTIFICATION_LIST -> "Hiện bạn chưa có thông báo phù hợp.";
                case CATEGORY_LIST -> "Hiện chưa có danh mục sản phẩm phù hợp.";
                case PRODUCT_BY_CATEGORY -> "Tôi chưa tìm thấy sản phẩm nào thuộc danh mục này.";
                case STOCK_BY_LOCATION -> "Vị trí này hiện chưa có tồn kho phù hợp.";
                case STOCK_BY_LOT -> "Tôi chưa tìm thấy tồn kho cho lô bạn hỏi.";
                case DEAD_STOCK -> "Hiện chưa có SKU nào thuộc nhóm hàng chết theo điều kiện này.";
                case STOCK_AT_RISK -> "Hiện chưa có lô tồn kho rủi ro theo điều kiện này.";
                case REORDER_SUGGESTION -> "Hiện chưa có SKU nào cần gợi ý đặt/nhập bổ sung.";
                case INVENTORY_VALUE_BY_WAREHOUSE -> "Hiện chưa có dữ liệu giá trị tồn kho theo kho phù hợp.";
                case LONGEST_EXPIRY_STOCK -> "Hiện chưa có lô hàng còn tồn với hạn sử dụng để xếp hạng.";
                case INBOUND_PRODUCT_QTY -> "Tôi chưa tìm thấy dữ liệu nhập kho phù hợp với sản phẩm/kỳ bạn hỏi.";
                case INBOUND_PENDING_PUTAWAY -> "Hiện chưa có phiếu nhập hoặc task nào đang chờ putaway.";
                case OUTBOUND_DELAYED -> "Hiện chưa có đơn xuất nào đang trễ hoặc có nguy cơ trễ theo dữ liệu hiện tại.";
                case STOCK_FASTEST_DECREASE -> "Hiện chưa có biến động giảm tồn kho trong 7 ngày qua.";
                case PICK_LOCATION_USAGE -> "Hiện chưa có dữ liệu vị trí lấy hàng phù hợp.";
                case OUTBOUND_DELAY_REASON -> "Hiện chưa ghi nhận lý do xuất chậm trong audit log.";
                case CYCLE_COUNT_RECOUNT_SKUS -> "Hiện chưa có SKU cần kiểm kê lại theo dữ liệu sai lệch.";
                case RMA_QC_REQUIRED -> "Hiện chưa có RMA đang chờ kiểm tra chất lượng.";
                case RMA_DETAIL -> "Tôi chưa tìm thấy RMA phù hợp.";
                case RMA_LATEST_REASON -> "Hiện chưa có RMA nào trong dữ liệu.";
                case RMA_BY_SKU -> "Tôi chưa tìm thấy RMA liên quan SKU/sản phẩm bạn hỏi.";
                case RMA_SUPPLIER_RETURN -> "Hiện chưa có RMA nào cần gửi về nhà cung cấp.";
                case TOP_CUSTOMERS -> "Hiện chưa có dữ liệu khách hàng phù hợp để xếp hạng.";
                case SUPPLIER_PERFORMANCE -> "Hiện chưa có dữ liệu hiệu suất nhà cung cấp phù hợp.";
                case WAREHOUSE_CAPACITY -> "Hiện chưa có dữ liệu công suất kho phù hợp.";
                case PICKING_PRODUCTIVITY -> "Hiện chưa có dữ liệu năng suất picking.";
                case USER_LOOKUP -> "Tôi chưa tìm thấy người dùng phù hợp.";
                case ROLE_LIST -> "Hiện chưa có dữ liệu vai trò.";
                default -> "Tôi chưa tìm thấy dữ liệu phù hợp với câu hỏi này.";
            };
        }
        if (route.getIntent() == AiIntent.WAREHOUSE_COUNT && data instanceof Map<?, ?> map) {
            Object total = map.get("total");
            Object active = map.get("active");
            Object inactive = map.get("inactive");
            return "Hệ thống hiện có " + total + " kho, trong đó " + active + " kho đang hoạt động và "
                    + inactive + " kho ngừng hoạt động.";
        }
        if (data instanceof Map<?, ?> map) {
            if (route.getIntent() == AiIntent.PURCHASE_ORDER_STATUS && Boolean.TRUE.equals(route.safeParameters().get("countOnly"))) {
                return purchaseOrderCountReply(map);
            }
            return switch (route.getIntent()) {
                case PRODUCT_COUNT -> productCountReply(map);
                case LOCATION_COUNT -> locationCountReply(map, route);
                case PRODUCT_LIST -> productListReply(map);
                case SUPPLIER_LIST, SUPPLIER_SEARCH -> supplierListReply(map);
                case CUSTOMER_LIST, CUSTOMER_SEARCH -> customerListReply(map);
                case GLOBAL_SEARCH -> globalSearchReply(map);
                case STOCK_TOTAL -> "Tổng tồn kho hiện có: " + value(map, "qty_on_hand") + " đơn vị, đã giữ chỗ "
                        + value(map, "qty_reserved") + ", khả dụng " + value(map, "qty_available")
                        + " trên " + value(map, "stocked_skus") + " SKU có tồn.";
                case LOT_TRACKED_COUNT -> "Có " + value(map, "lot_tracked") + " / " + value(map, "total")
                        + " sản phẩm active đang được theo dõi lot number. Sản phẩm theo dõi hạn dùng: "
                        + value(map, "expiry_tracked") + ".";
                case DAILY_TASKS -> dailyTasksReply(map);
                case REPORT_SUMMARY -> reportSummaryReply(map);
                case FLOW_REPORT -> flowReportReply(map);
                case INBOUND_REPORT -> inboundReportReply(map);
                case OUTBOUND_REPORT -> outboundReportReply(map, route);
                case MONTHLY_REPORT -> monthlyReportReply(map);
                case INVENTORY_VALUE -> inventoryValueReply(map);
                case INACTIVE_PRODUCT_WITH_STOCK -> inactiveProductWithStockReply(map);
                case INBOUND_PRODUCT_QTY -> inboundProductQtyReply(map);
                case INBOUND_AVG_DAILY -> inboundAvgDailyReply(map);
                case OUTBOUND_TOTAL_QTY -> outboundTotalQtyReply(map);
                case OUTBOUND_CANCELLED_OR_RETURNED -> outboundCancelledOrReturnedReply(map);
                case OUTBOUND_SHORTAGE -> outboundShortageReply(map);
                case PICKING_COMPLETED_COUNT -> pickingCompletedCountReply(map);
                case PICKING_COMPLETION_RATE -> pickingCompletionRateReply(map);
                case PICKING_STOCK_CHECK -> pickingStockCheckReply(map);
                case CYCLE_COUNT_SUMMARY -> cycleCountSummaryReply(map);
                case CYCLE_COUNT_COMPLETION_RATE -> cycleCountCompletionRateReply(map);
                case RMA_RATE -> rmaRateReply(map);
                case RMA_PROCESSING_AVG -> rmaProcessingAvgReply(map);
                case RMA_VALUE -> rmaValueReply(map);
                case TASK_COMPLETION_RATE -> taskCompletionRateReply(map);
                case EMPLOYEE_OPERATION_PRODUCTIVITY -> employeeOperationProductivityReply(map);
                case OVERDUE_TASKS -> overdueTasksReply(map);
                case OPERATION_ISSUE_REPORT -> operationIssueReportReply(map);
                case MONTH_OVER_MONTH_FLOW -> monthOverMonthFlowReply(map);
                case FULFILLMENT_RATE -> fulfillmentRateReply(map);
                case MY_TASKS -> myTasksReply(map);
                default -> null;
            };
        }
        if (data instanceof List<?> list) {
            if (route.getIntent() == AiIntent.SALES_ORDER_STATUS && Boolean.TRUE.equals(route.safeParameters().get("latestOnly"))) {
                return latestSalesOrderReply(list);
            }
            return switch (route.getIntent()) {
                case WAREHOUSE_LIST -> warehouseListReply(list);
                case WAREHOUSE_DETAIL -> warehouseDetailReply(list);
                case PRODUCT_DETAIL -> productDetailReply(list);
                case SUPPLIER_DETAIL -> supplierDetailReply(list);
                case SUPPLIER_TOP -> supplierTopReply(list);
                case CUSTOMER_DETAIL -> customerDetailReply(list);
                case STOCK_BY_PRODUCT -> stockByProductReply(list, route);
                case STOCK_LOWEST -> stockLowestReply(list);
                case STOCK_HIGHEST -> stockHighestReply(list);
                case PRODUCT_BY_BARCODE -> productByBarcodeReply(list, route);
                case STOCK_BELOW_THRESHOLD -> stockBelowThresholdReply(list);
                case LOW_STOCK -> lowStockReply(list);
                case SLOW_MOVING_STOCK -> slowMovingStockReply(list);
                case WAREHOUSE_STOCK_SUMMARY -> warehouseStockSummaryReply(list);
                case BEST_HEAVY_LOCATION -> heavyLocationReply(list);
                case NEAR_EXPIRY -> nearExpiryReply(list);
                case PENDING_PUTAWAY -> putawayReply(list, route);
                case PUTAWAY_BY_WAREHOUSE -> putawayByWarehouseReply(list);
                case INBOUND_TODAY -> inboundTodayReply(list);
                case LATEST_INBOUND -> latestInboundReply(list);
                case INBOUND_RECEIPT_STATUS -> inboundReceiptStatusReply(list);
                case INBOUND_RECEIPT_DETAIL -> inboundReceiptDetailReply(list, route);
                case PENDING_PO_RECEIPT -> pendingPoReceiptReply(list);
                case PURCHASE_ORDER_STATUS -> purchaseOrderReply(list);
                case PURCHASE_ORDER_DETAIL -> purchaseOrderDetailReply(list);
                case PURCHASE_ORDER_APPROVAL_AUDIT -> purchaseOrderApprovalAuditReply(list);
                case OUTBOUND_PRIORITY -> outboundPriorityReply(list);
                case PACKING_STATUS -> packingReply(list);
                case PICKING_TOP -> pickingTopReply(list);
                case PICKING_STATUS -> pickingReply(list, route);
                case SALES_TOP -> salesTopReply(list);
                case SALES_ORDER_STATUS -> salesOrderStatusReply(list, route);
                case SALES_ORDER_DETAIL -> salesOrderDetailReply(list, route);
                case STOCK_MOVEMENT_HISTORY -> stockMovementReply(list);
                case ACTIVE_CYCLE_COUNTS -> activeCycleCountReply(list);
                case CYCLE_COUNT_VARIANCE -> cycleVarianceReply(list, route);
                case CYCLE_COUNT_STATUS -> cycleCountStatusReply(list);
                case RMA_PENDING -> rmaReply(list, route);
                case LOCATION_SEARCH -> locationReply(list);
                case AUDIT_LOG -> auditLogReply(list);
                case AI_AUDIT_LOG -> aiAuditLogReply(list);
                case NOTIFICATION_LIST -> notificationReply(list);
                case CATEGORY_LIST -> categoryReply(list);
                case PRODUCT_BY_CATEGORY -> productByCategoryReply(list);
                case STOCK_BY_LOCATION -> stockByLocationReply(list);
                case STOCK_BY_LOT -> stockByLotReply(list);
                case DEAD_STOCK -> deadStockReply(list);
                case STOCK_AT_RISK -> stockAtRiskReply(list);
                case REORDER_SUGGESTION -> reorderSuggestionReply(list);
                case INVENTORY_VALUE_BY_WAREHOUSE -> inventoryValueByWarehouseReply(list);
                case LONGEST_EXPIRY_STOCK -> longestExpiryReply(list);
                case INBOUND_PENDING_PUTAWAY -> inboundPendingPutawayReply(list);
                case OUTBOUND_DELAYED -> outboundDelayedReply(list);
                case STOCK_FASTEST_DECREASE -> stockFastestDecreaseReply(list);
                case PICK_LOCATION_USAGE -> pickLocationUsageReply(list);
                case OUTBOUND_DELAY_REASON -> outboundDelayReasonReply(list);
                case CYCLE_COUNT_RECOUNT_SKUS -> cycleCountRecountSkusReply(list);
                case RMA_DETAIL -> rmaDetailReply(list);
                case RMA_LATEST_REASON -> latestRmaReasonReply(list);
                case RMA_BY_SKU -> rmaBySkuReply(list);
                case RMA_SUPPLIER_RETURN -> rmaSupplierReturnReply(list);
                case RMA_QC_REQUIRED -> rmaQcRequiredReply(list);
                case TOP_CUSTOMERS -> topCustomersReply(list);
                case WAREHOUSE_CAPACITY -> warehouseCapacityReply(list);
                case PICKING_PRODUCTIVITY -> pickingProductivityReply(list);
                case SUPPLIER_PERFORMANCE -> supplierPerformanceReply(list);
                case USER_LOOKUP -> userLookupReply(list);
                case ROLE_LIST -> roleListReply(list);
                default -> null;
            };
        }
        return null;
    }

    private String warehouseListReply(List<?> list) {
        return "Có " + list.size() + " kho trong danh sách: " + joinRows(list, row ->
                value(row, "code") + " - " + value(row, "name") + " (" + activeText(value(row, "is_active")) + ")");
    }

    private String warehouseDetailReply(List<?> list) {
        return joinRows(list, row -> value(row, "code") + " - " + value(row, "name")
                + ", quản lý: " + value(row, "manager_name")
                + ", trạng thái: " + activeText(value(row, "is_active")));
    }

    private String locationCountReply(Map<?, ?> map, AiIntentResult route) {
        if ("false".equalsIgnoreCase(value(map, "warehouse_found"))) {
            return "Tôi chưa tìm thấy kho phù hợp với thông tin bạn nêu.";
        }
        String query = routeQuery(route);
        if (map.containsKey("warehouse_code")
                && containsAny(query, "slot trong", "vi tri trong", "con slot", "cho trong", "empty slot")) {
            return value(map, "warehouse_code") + " hiện còn " + formatNumber(value(map, "available"))
                    + " vị trí lưu trữ trống.";
        }
        if (map.containsKey("warehouse_code")) {
            return "Kho " + value(map, "warehouse_code") + " có " + value(map, "storage")
                    + " vị trí lưu trữ, tổng " + value(map, "total") + " vị trí; available "
                    + value(map, "available") + ", maintenance " + value(map, "maintenance") + ".";
        }
        return "Toàn hệ thống có " + value(map, "total") + " vị trí; available "
                + value(map, "available") + ", maintenance " + value(map, "maintenance") + ".";
    }

    private String productListReply(Map<?, ?> map) {
        Object itemsObject = map.get("items");
        if (!(itemsObject instanceof List<?> items)) {
            return "Tôi chưa lấy được danh sách sản phẩm phù hợp.";
        }
        if (items.isEmpty()) {
            return "Hiện chưa có sản phẩm nào phù hợp với điều kiện bạn hỏi.";
        }
        return "Có " + value(map, "total") + " sản phẩm trong hệ thống. Một số sản phẩm gần nhất: "
                + joinRows(limit(items, 5), row -> value(row, "sku") + " - " + value(row, "product_name")
                + " (" + value(row, "category_name") + ", " + statusValue(row, "status") + ")");
    }

    private String productCountReply(Map<?, ?> map) {
        Object byWarehouseObject = map.get("byWarehouse");
        if (byWarehouseObject instanceof List<?> byWarehouse && !byWarehouse.isEmpty()) {
            return "Hệ thống hiện có " + value(map, "total") + " sản phẩm. Theo từng kho: "
                    + joinRows(limit(byWarehouse, 8), row -> value(row, "warehouse_code") + " - "
                    + value(row, "warehouse_name") + ": " + value(row, "product_count") + " sản phẩm có tồn");
        }
        return "Hệ thống hiện có " + value(map, "total") + " sản phẩm.";
    }

    private String supplierListReply(Map<?, ?> map) {
        Object itemsObject = map.get("items");
        if (!(itemsObject instanceof List<?> items) || items.isEmpty()) {
            return "Tôi chưa tìm thấy nhà cung cấp phù hợp.";
        }
        return "Tìm thấy " + value(map, "total") + " nhà cung cấp. Một số dòng: "
                + joinRows(limit(items, 6), row -> value(row, "code") + " - " + value(row, "name")
                + " (" + statusValue(row, "status") + ", liên hệ " + value(row, "contact_name") + ")");
    }

    private String customerListReply(Map<?, ?> map) {
        Object itemsObject = map.get("items");
        if (!(itemsObject instanceof List<?> items) || items.isEmpty()) {
            return "Tôi chưa tìm thấy khách hàng phù hợp.";
        }
        return "Tìm thấy " + value(map, "total") + " khách hàng. Một số dòng: "
                + joinRows(limit(items, 6), row -> value(row, "code") + " - " + value(row, "name")
                + " (" + activeText(value(row, "is_active")) + ", liên hệ " + value(row, "contact_name") + ")");
    }

    private String productDetailReply(List<?> list) {
        return "Chi tiết sản phẩm: " + joinRows(limit(list, 5), row -> value(row, "sku") + " - "
                + value(row, "product_name") + ", danh mục " + value(row, "category_name")
                + ", NCC " + value(row, "supplier_name") + ", tồn " + value(row, "qty_on_hand")
                + ", khả dụng " + value(row, "qty_available") + ", trạng thái " + statusValue(row, "status"));
    }

    private String supplierDetailReply(List<?> list) {
        return "Chi tiết nhà cung cấp: " + joinRows(limit(list, 5), row -> value(row, "code") + " - "
                + value(row, "name") + ", liên hệ " + value(row, "contact_name")
                + ", điện thoại " + value(row, "contact_phone") + ", email " + value(row, "contact_email")
                + ", trạng thái " + statusValue(row, "status"));
    }

    private String supplierTopReply(List<?> list) {
        Object first = list.get(0);
        return "Nhà cung cấp có nhiều phiếu nhập nhất là " + value(first, "supplier_name")
                + " với " + value(first, "purchase_order_count") + " phiếu, tổng giá trị "
                + value(first, "total_amount") + ". Top hiện tại: "
                + joinRows(limit(list, 5), row -> value(row, "supplier_name") + " - "
                + value(row, "purchase_order_count") + " phiếu, SL đặt " + value(row, "ordered_qty"));
    }

    private String customerDetailReply(List<?> list) {
        return "Chi tiết khách hàng: " + joinRows(limit(list, 5), row -> value(row, "code") + " - "
                + value(row, "name") + ", liên hệ " + value(row, "contact_name")
                + ", điện thoại " + value(row, "phone") + ", email " + value(row, "email")
                + ", trạng thái " + activeText(value(row, "is_active")));
    }

    private String stockByProductReply(List<?> list, AiIntentResult route) {
        Object first = list.get(0);
        String query = routeQuery(route);
        String productLabel = productLabel(first);
        long totalOnHand = sumLong(list, "qty_on_hand");
        long totalReserved = sumLong(list, "qty_reserved");
        long totalAvailable = sumLong(list, "qty_available");
        Map<String, long[]> byWarehouse = stockByWarehouse(list);

        if (containsAny(query, "o kho nao", "tai kho nao", "kho nao co", "nam o kho nao")
                && !(query.contains("nhieu") && query.contains("nhat"))) {
            String warehouses = byWarehouse.keySet().stream()
                    .filter(code -> !"N/A".equals(code))
                    .map(code -> "**" + code + "**")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("chưa xác định kho");
            return productLabel + " hiện đang có tại các kho: " + warehouses + ".";
        }

        if (containsAny(query, "giu cho", "reserved", "bi hold", "dang giu")) {
            if (totalReserved > 0) {
                return "Có, sản phẩm " + productLabel + " hiện đang giữ chỗ **" + formatNumber(totalReserved) + "** đơn vị.";
            }
            return "Không, hiện chưa ghi nhận số lượng giữ chỗ cho " + productLabel + ".";
        }

        if (containsAny(query, "xuat duoc", "xuat toi da", "co the xuat", "ban duoc bao nhieu")) {
            return "Bạn có thể xuất tối đa **" + formatNumber(totalAvailable) + "** đơn vị cho " + productLabel + ".";
        }

        if (query.contains("kho nao") && query.contains("nhieu") && query.contains("nhat")) {
            Map.Entry<String, long[]> max = byWarehouse.entrySet().stream()
                    .max((a, b) -> Long.compare(a.getValue()[0], b.getValue()[0]))
                    .orElse(null);
            if (max != null) {
                return "Kho **" + max.getKey() + "** hiện có tồn kho cao nhất cho " + productLabel + " với **" + formatNumber(max.getValue()[0]) + "** đơn vị.";
            }
        }

        String warehouseCode = singleWarehouseCode(byWarehouse);
        if (warehouseCode != null) {
            String availableText = totalAvailable == totalOnHand
                    ? "khả dụng toàn bộ"
                    : "khả dụng **" + formatNumber(totalAvailable) + "** đơn vị";
            return "**Thông tin tồn kho " + productLabel + ":**\n" +
                   "- **Tại kho:** **" + warehouseCode + "**\n" +
                   "- **Tồn kho hiện có:** **" + formatNumber(totalOnHand) + "** đơn vị\n" +
                   "- **Trạng thái khả dụng:** " + availableText + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Thông tin tồn kho ").append(productLabel).append(" trên toàn hệ thống:**\n")
          .append("- **Tổng tồn hiện có:** **").append(formatNumber(totalOnHand)).append("** đơn vị\n")
          .append("- **Khả dụng:** **").append(formatNumber(totalAvailable)).append("** đơn vị\n")
          .append("- **Đang giữ chỗ:** **").append(formatNumber(totalReserved)).append("** đơn vị\n\n")
          .append("**Chi tiết theo từng kho:**\n");
          
        for (Map.Entry<String, long[]> entry : byWarehouse.entrySet()) {
            if ("N/A".equals(entry.getKey())) continue;
            sb.append("- Kho **").append(entry.getKey()).append("**: tồn **")
              .append(formatNumber(entry.getValue()[0])).append("** đơn vị (khả dụng **")
              .append(formatNumber(entry.getValue()[2])).append("**)\n");
        }
        return sb.toString().trim();
    }

    private String stockLowestReply(List<?> list) {
        String prefix = "**Danh sách sản phẩm có tồn khả dụng thấp nhất:**\n\n";
        String items = joinRowsAsBulletList(limit(list, 5), row -> 
            value(row, "product_name") + " (SKU: **" + value(row, "sku") + "**) - Khả dụng: **" + formatNumber(longValue(row, "qty_available")) + "** " +
            "(Tồn hiện có: " + formatNumber(longValue(row, "qty_on_hand")) + ", Định mức tối thiểu: " + formatNumber(longValue(row, "min_stock_qty")) + ")"
        );
        return prefix + items;
    }

    private String stockHighestReply(List<?> list) {
        Object first = list.get(0);
        String prefix = "**Sản phẩm có tồn kho cao nhất:** **" + value(first, "product_name") + "**\n" +
                       "- **Mã SKU:** **" + value(first, "sku") + "**\n" +
                       "- **Số lượng tồn hiện có:** **" + formatNumber(longValue(first, "qty_on_hand")) + "** đơn vị\n" +
                       "- **Khả dụng:** **" + formatNumber(longValue(first, "qty_available")) + "** đơn vị\n\n" +
                       "**Danh sách Top 5 sản phẩm tồn kho cao nhất:**\n";
        String items = joinRowsAsBulletList(limit(list, 5), row -> 
            value(row, "product_name") + " (SKU: **" + value(row, "sku") + "**): **" + formatNumber(longValue(row, "qty_on_hand")) + "** đơn vị"
        );
        return prefix + items;
    }

    private String productByBarcodeReply(List<?> list, AiIntentResult route) {
        if (containsAny(routeQuery(route), "khong ra", "khong nhan", "scan loi", "scan khong")) {
            return "Barcode chưa được đăng ký hoặc không tồn tại.";
        }
        return joinRows(list, row -> "Barcode " + value(row, "barcode_ean13") + " thuộc SKU "
                + value(row, "sku") + " - " + value(row, "product_name") + ".");
    }

    private String stockBelowThresholdReply(List<?> list) {
        return "Có " + list.size() + " sản phẩm dưới ngưỡng tồn kho yêu cầu. Một số dòng: "
                + joinRows(limit(list, 8), row -> value(row, "product_name") + " tồn " + value(row, "qty_on_hand"));
    }

    private String lowStockReply(List<?> list) {
        return "Có " + list.size() + " SKU dưới reorder point, bao gồm "
                + joinRows(limit(list, 5), row -> "SKU " + value(row, "sku")) + ".";
    }

    private String slowMovingStockReply(List<?> list) {
        Object first = list.get(0);
        String days = value(first, "days_without_movement");
        if ("N/A".equals(days)) {
            return "SKU " + value(first, "sku") + " chưa từng phát sinh giao dịch tồn kho theo dữ liệu hiện tại.";
        }
        return "SKU " + value(first, "sku") + " không phát sinh giao dịch " + formatNumber(days) + " ngày.";
    }

    private String purchaseOrderCountReply(Map<?, ?> map) {
        String dateRange = value(map, "dateRange");
        String scope = "TODAY".equalsIgnoreCase(dateRange) ? "hôm nay" : "trong phạm vi bạn hỏi";
        return "Hiện có " + value(map, "total") + " phiếu nhập ở trạng thái chờ duyệt " + scope + ".";
    }

    private String warehouseStockSummaryReply(List<?> list) {
        if (hasKey(firstMap(list), "product_group")) {
            return joinRows(list, row -> value(row, "product_group") + " tại " + value(row, "warehouse_code")
                    + ": tồn " + value(row, "qty_on_hand") + ", giữ chỗ " + value(row, "qty_reserved")
                    + ", khả dụng " + value(row, "qty_available"));
        }
        Object first = list.get(0);
        return "Kho có nhiều hàng tồn nhất là " + value(first, "warehouse_code") + " - "
                + value(first, "warehouse_name") + " với " + value(first, "qty_on_hand")
                + " đơn vị tồn hiện có. Chi tiết: " + joinRows(list, row -> value(row, "warehouse_code")
                + " tồn " + value(row, "qty_on_hand") + ", khả dụng " + value(row, "qty_available"));
    }

    private String heavyLocationReply(List<?> list) {
        Object first = list.get(0);
        return "Vị trí phù hợp nhất cho hàng nặng là " + value(first, "warehouse_code") + "/"
                + value(first, "location_code") + ", zone " + value(first, "zone")
                + ", sức chứa kg " + value(first, "max_weight_kg") + ".";
    }

    private String nearExpiryReply(List<?> list) {
        List<?> displayed = limit(list, 10);
        String prefix = "**Phát hiện " + list.size() + " lô hàng sắp hết hạn hoặc đã quá hạn:**\n\n";
        String items = joinRowsAsBulletList(displayed, row -> {
            long daysLeft = longValue(value(row, "days_left"));
            String statusText = daysLeft < 0 
                ? "**Đã quá hạn " + Math.abs(daysLeft) + " ngày**" 
                : "còn **" + daysLeft + " ngày**";
            return value(row, "product_name") + " (Kho **" + value(row, "warehouse_code") + "**) " +
                   "- Hạn dùng: `" + value(row, "expiry_date") + "` (" + statusText + ")";
        });
        String suffix = list.size() > displayed.size() 
            ? "\n\n*(Chỉ hiển thị " + displayed.size() + " lô hàng khẩn cấp nhất)*" 
            : "";
        return prefix + items + suffix;
    }

    private String putawayReply(List<?> list, AiIntentResult route) {
        String query = routeQuery(route);
        Object first = list.get(0);
        String taskCode = routeParam(route, "putawayTaskCode", "taskCode", "code");
        if (taskCode != null) {
            if (containsAny(query, "dua hang di dau", "di dau", "de hang", "cat vao dau", "vao bin nao")) {
                return "Chuyển SKU " + value(first, "sku") + " vào " + value(first, "suggested_location") + ".";
            }
            if (containsAny(query, "xong chua", "hoan tat chua", "done", "completed")) {
                return taskCode + " đang ở trạng thái " + statusLabel(value(first, "status")) + ".";
            }
            return taskCode + " trạng thái " + statusLabel(value(first, "status"))
                    + ", vị trí gợi ý " + value(first, "suggested_location") + ".";
        }
        return "Có " + list.size() + " putaway task đang chờ/xử lý: " + joinRows(limit(list, 6), row ->
                value(row, "product_name") + " số lượng " + value(row, "qty_to_putaway")
                        + ", trạng thái " + statusValue(row, "status")
                        + ", vị trí gợi ý " + value(row, "suggested_location"));
    }

    private String putawayByWarehouseReply(List<?> list) {
        Object first = list.get(0);
        return "Kho có nhiều putaway task nhất là " + value(first, "warehouse_code")
                + " với " + value(first, "task_count") + " task, tổng số lượng "
                + value(first, "qty_to_putaway") + ". Chi tiết: "
                + joinRows(list, row -> value(row, "warehouse_code") + " có "
                + value(row, "task_count") + " task");
    }

    private String inboundTodayReply(List<?> list) {
        return "Hôm nay có " + list.size() + " dòng hàng nhập: " + joinRows(limit(list, 8), row ->
                value(row, "receipt_number") + " - " + value(row, "product_name")
                        + " số lượng " + value(row, "received_qty")
                        + ", NCC " + value(row, "supplier_name"));
    }

    private String latestInboundReply(List<?> list) {
        Object first = list.get(0);
        return "Phiếu nhập gần nhất là " + value(first, "receipt_number") + " ngày "
                + value(first, "received_date") + ", thuộc PO " + value(first, "po_number")
                + ", nhà cung cấp " + value(first, "supplier_name")
                + ". Dòng hàng gần nhất: " + joinRows(limit(list, 5), row -> value(row, "product_name")
                + " số lượng " + value(row, "received_qty"));
    }

    private String inboundReceiptStatusReply(List<?> list) {
        Object first = list.get(0);
        String receipt = value(first, "receipt_number");
        String status = value(first, "receipt_status");
        boolean waitingPutaway = list.stream()
                .map(row -> value(row, "putaway_status"))
                .anyMatch(statusValue -> List.of("PENDING", "IN_PROGRESS", "ASSIGNED").contains(statusValue));
        if ("RECEIVED".equalsIgnoreCase(status) && waitingPutaway) {
            return "Goods Receipt " + receipt + " đã hoàn tất nhận hàng, chờ putaway.";
        }
        return "Goods Receipt " + receipt + " đang ở trạng thái " + statusLabel(status) + ".";
    }

    private String inboundReceiptDetailReply(List<?> list, AiIntentResult route) {
        String query = routeQuery(route);
        Object first = list.get(0);
        String receipt = value(first, "receipt_number");
        if (containsAny(query, "ai dang", "ai xu ly", "nguoi xu ly", "assigned", "gan cho ai")) {
            String assignee = firstNonBlank(list, "assignee_username");
            return "Task được gán cho " + ("N/A".equals(assignee) ? "N/A" : assignee) + ".";
        }
        String location = firstNonBlank(list, "suggested_location");
        String warehouse = firstNonBlank(list, "suggested_warehouse_code");
        return "Hệ thống đề xuất putaway vào " + location + ", " + warehouse + ".";
    }

    private String purchaseOrderReply(List<?> list) {
        return "Tìm thấy " + list.size() + " đơn nhập: " + joinRows(limit(list, 5), row ->
                value(row, "po_number") + " trạng thái " + statusValue(row, "status")
                        + ", NCC " + value(row, "supplier_name")
                        + ", dự kiến " + value(row, "expected_date"));
    }

    private String purchaseOrderDetailReply(List<?> list) {
        Object first = list.get(0);
        return "PO " + value(first, "po_number") + " trạng thái " + statusValue(first, "status")
                + ", NCC " + value(first, "supplier_name") + ", kho " + value(first, "warehouse_code")
                + ". Dòng hàng: " + joinRows(limit(list, 8), row -> "#" + value(row, "line_number")
                + " " + value(row, "sku") + " đặt " + value(row, "ordered_qty")
                + ", đã nhận " + value(row, "received_qty"));
    }

    private String purchaseOrderApprovalAuditReply(List<?> list) {
        Object first = list.get(0);
        return "Phiếu nhập " + value(first, "po_number") + " được duyệt bởi " + value(first, "actor_name")
                + " (" + value(first, "actor_email") + ") vào " + value(first, "created_at")
                + (value(first, "reason").equals("N/A") ? "." : ", lý do: " + value(first, "reason") + ".");
    }

    private String pendingPoReceiptReply(List<?> list) {
        return "Có " + list.size() + " PO đang chờ nhận/chưa hoàn tất: " + joinRows(limit(list, 6), row ->
                value(row, "po_number") + " trạng thái " + statusValue(row, "status")
                        + ", còn phải nhận " + value(row, "remaining_qty")
                        + ", NCC " + value(row, "supplier_name"));
    }

    private String outboundPriorityReply(List<?> list) {
        Object first = list.get(0);
        return "Đơn xuất ưu tiên cao nhất hiện tại là " + value(first, "so_number")
                + " của " + value(first, "customer_name")
                + ", priority " + value(first, "priority")
                + ", trạng thái " + statusValue(first, "status") + ".";
    }

    private String salesOrderStatusReply(List<?> list, AiIntentResult route) {
        String query = routeQuery(route);
        Object first = list.get(0);
        if (list.size() == 1 || routeParam(route, "soId", "code") != null) {
            String status = value(first, "status");
            if (containsAny(query, "hoan tat", "xong chua", "da xong", "da hoan thanh")) {
                if (containsAny(normalize(status), "shipped", "completed")) {
                    return "Đã, đơn " + value(first, "so_number") + " đã hoàn tất.";
                }
                if ("PICKING".equalsIgnoreCase(status)) {
                    return "Chưa, đơn vẫn đang ở bước picking.";
                }
                return "Chưa, đơn hiện ở trạng thái " + statusLabel(status) + ".";
            }
            if (containsAny(query, "trang thai", "tinh trang", "status", "dang o trang thai")) {
                return "Sales order " + value(first, "so_number") + " đang ở trạng thái "
                        + statusLabel(status) + ".";
            }
        }
        return "Tìm thấy " + list.size() + " đơn xuất: " + joinRows(limit(list, 6), row ->
                value(row, "so_number") + " trạng thái " + statusValue(row, "status")
                        + ", khách " + value(row, "customer_name")
                        + ", đặt " + value(row, "ordered_qty")
                        + ", đã giao " + value(row, "shipped_qty"));
    }

    private String latestSalesOrderReply(List<?> list) {
        Object first = list.get(0);
        return "Phiếu xuất được duyệt gần nhất là " + value(first, "so_number")
                + ", trạng thái " + statusValue(first, "status")
                + ", khách " + value(first, "customer_name")
                + ", tạo lúc " + value(first, "created_at") + ".";
    }

    private String salesOrderDetailReply(List<?> list, AiIntentResult route) {
        String query = routeQuery(route);
        Object first = list.get(0);
        if (containsAny(query, "con thieu", "thieu gi", "thieu bao nhieu", "chua du")) {
            List<String> missing = list.stream()
                    .map(row -> {
                        long remaining = Math.max(longValue(row, "ordered_qty") - longValue(row, "shipped_qty"), 0);
                        if (remaining <= 0) {
                            return null;
                        }
                        return formatNumber(remaining) + " đơn vị SKU " + value(row, "sku");
                    })
                    .filter(item -> item != null)
                    .toList();
            if (missing.isEmpty()) {
                return "Đơn " + value(first, "so_number") + " không còn thiếu số lượng theo dữ liệu hiện tại.";
            }
            return "Đơn còn thiếu " + String.join("; ", missing) + ".";
        }
        return "SO " + value(first, "so_number") + " trạng thái " + statusValue(first, "status")
                + ", khách " + value(first, "customer_name") + ", kho " + value(first, "warehouse_code")
                + ". Dòng hàng: " + joinRows(limit(list, 8), row -> "#" + value(row, "line_number")
                + " " + value(row, "sku") + " đặt " + value(row, "ordered_qty")
                + ", đã giao " + value(row, "shipped_qty"));
    }

    private String packingReply(List<?> list) {
        return "Có " + list.size() + " đơn xuất đang ở bước trước/trong packing: " + joinRows(limit(list, 6), row ->
                value(row, "so_number") + " trạng thái " + statusValue(row, "status")
                        + ", khách " + value(row, "customer_name"));
    }

    private String pickingTopReply(List<?> list) {
        Object first = list.get(0);
        return "Sản phẩm được picking nhiều nhất là " + value(first, "product_name")
                + " với số lượng cần pick " + value(first, "qty_to_pick")
                + ". Top: " + joinRows(limit(list, 5), row -> value(row, "product_name")
                + " cần pick " + value(row, "qty_to_pick"));
    }

    private String salesTopReply(List<?> list) {
        return "Top sản phẩm bán/chốt đơn theo dữ liệu hiện có: " + joinRows(limit(list, 5), row ->
                value(row, "product_name") + " đã giao " + value(row, "shipped_qty")
                        + ", đặt " + value(row, "ordered_qty")
                        + ", số đơn " + value(row, "order_count"));
    }

    private String pickingReply(List<?> list, AiIntentResult route) {
        String query = routeQuery(route);
        Object first = list.get(0);
        if (containsAny(query, "con bao nhieu mon", "bao nhieu mon chua", "chua lay", "chua hoan tat")) {
            long remainingSku = list.stream()
                    .filter(row -> longValue(row, "remaining_qty") > 0 || !"PICKED".equalsIgnoreCase(value(row, "status")))
                    .map(row -> value(row, "sku"))
                    .distinct()
                    .count();
            return "Còn " + formatNumber(remainingSku) + " SKU chưa hoàn tất picking.";
        }
        if (containsAny(query, "uu tien", "priority")) {
            long priority = longValue(first, "priority");
            if (priority > 0 && priority <= 2) {
                return "Đây là task ưu tiên cao do đơn giao gấp.";
            }
            return "Task này không nằm trong nhóm ưu tiên cao theo dữ liệu hiện tại.";
        }
        if (containsAny(query, "ai dang", "ai pick", "ai lay hang", "nguoi pick", "assigned")) {
            return "Task đang được xử lý bởi " + value(first, "assignee_username") + ".";
        }
        if (containsAny(query, "lay hang o dau", "lay tu dau", "lay hang tai dau", "bin nao", "vi tri nao")) {
            return "Lấy " + formatNumber(value(first, "qty_to_pick")) + " đơn vị SKU " + value(first, "sku")
                    + " từ " + value(first, "location_code") + ", " + value(first, "warehouse_code") + ".";
        }
        return "Có " + list.size() + " picking task/dòng picking phù hợp: " + joinRows(limit(list, 6), row ->
                value(row, "so_number") + " - " + value(row, "product_name")
                        + ", cần pick " + value(row, "qty_to_pick")
                        + ", đã pick " + value(row, "qty_picked")
                        + ", trạng thái " + statusValue(row, "status"));
    }

    private String stockMovementReply(List<?> list) {
        return "Có " + list.size() + " dòng lịch sử biến động tồn kho: " + joinRows(limit(list, 8), row ->
                value(row, "created_at") + " - " + value(row, "sku") + " tại "
                        + value(row, "warehouse_code") + "/" + value(row, "location_code")
                        + ", loại " + value(row, "movement_type")
                        + ", thay đổi " + value(row, "qty_change")
                        + ", sau biến động " + value(row, "qty_after"));
    }

    private String activeCycleCountReply(List<?> list) {
        return "Có " + list.size() + " cycle count đang mở: " + joinRows(list, row ->
                value(row, "cycle_count_id") + " tại " + value(row, "warehouse_code")
                        + ", trạng thái " + statusValue(row, "status")
                        + ", số dòng " + value(row, "item_count"));
    }

    private String cycleVarianceReply(List<?> list, AiIntentResult route) {
        String query = routeQuery(route);
        if (containsAny(query, "co lech", "lech sku", "chenh lech") && routeParam(route, "sku") != null) {
            Object first = list.get(0);
            long discrepancy = longValue(first, "discrepancy");
            if (discrepancy != 0) {
                return "Có, chênh lệch " + formatNumber(discrepancy) + " đơn vị.";
            }
            return "Không, SKU này chưa ghi nhận chênh lệch trong session kiểm kê.";
        }
        return "Có " + list.size() + " dòng kiểm kê đang lệch hoặc chờ đếm: " + joinRows(limit(list, 6), row ->
                value(row, "cycle_count_id") + " - " + value(row, "product_name")
                        + ", hệ thống " + value(row, "system_qty")
                        + ", đếm " + value(row, "counted_qty")
                        + ", chênh lệch " + value(row, "discrepancy"));
    }

    private String cycleCountStatusReply(List<?> list) {
        Object first = list.get(0);
        String status = value(first, "status");
        boolean locked = containsAny(normalize(status), "locked", "completed", "approved", "cancelled");
        if (locked) {
            return "Session kiểm kê " + value(first, "cycle_count_id") + " hiện đã khóa.";
        }
        return "Session kiểm kê " + value(first, "cycle_count_id") + " chưa khóa, trạng thái "
                + statusLabel(status) + ".";
    }

    private String rmaReply(List<?> list, AiIntentResult route) {
        String query = routeQuery(route);
        Object first = list.get(0);
        if (routeParam(route, "returnCode", "rmaId", "code") != null) {
            if (containsAny(query, "cong ton", "da cong ton", "nhap lai ton")) {
                if (containsAny(normalize(value(first, "status")), "completed", "closed")) {
                    return "Đã, hàng trả đã hoàn tất kiểm định và được cộng tồn.";
                }
                return "Chưa, hàng chưa hoàn tất kiểm định.";
            }
            return "Return đang " + returnStatusLabel(value(first, "status")) + ".";
        }
        return "Có " + list.size() + " yêu cầu RMA đang chờ xử lý: " + joinRows(limit(list, 5), row ->
                value(row, "rma_number") + " - " + value(row, "customer_name")
                        + ", trạng thái " + statusValue(row, "status"));
    }

    private String rmaDetailReply(List<?> list) {
        Object first = list.get(0);
        return value(first, "rma_number") + " đang " + returnStatusLabel(value(first, "status"))
                + ", khách hàng " + value(first, "customer_name")
                + ", lý do: " + value(first, "reason")
                + ". Dòng hàng: " + joinRows(limit(list, 5), row -> value(row, "sku")
                + " - " + value(row, "product_name") + ", SL " + value(row, "expected_qty"));
    }

    private String latestRmaReasonReply(List<?> list) {
        Object first = list.get(0);
        return "**Nguyên nhân trả hàng trong RMA mới nhất:**\n" +
               "- **Mã RMA:** `" + value(first, "rma_number") + "`\n" +
               "- **Lý do trả hàng:** *\"" + value(first, "reason") + "\"*.";
    }

    private String rmaBySkuReply(List<?> list) {
        return "Có " + list.size() + " dòng RMA liên quan SKU/sản phẩm bạn hỏi: "
                + joinRows(limit(list, 6), row -> value(row, "rma_number") + " - "
                + value(row, "sku") + " " + value(row, "product_name")
                + ", trạng thái " + statusValue(row, "status")
                + ", SL " + value(row, "expected_qty"));
    }

    private String rmaSupplierReturnReply(List<?> list) {
        return "Các RMA cần gửi về nhà cung cấp: " + joinRows(limit(list, 6), row ->
                value(row, "rma_number") + " - " + value(row, "customer_name")
                        + ", trạng thái " + statusValue(row, "status")
                        + ", SL " + value(row, "expected_qty"));
    }

    private String rmaQcRequiredReply(List<?> list) {
        return "Các RMA cần kiểm tra chất lượng trước khi nhập lại: "
                + joinRows(limit(list, 6), row -> value(row, "rma_number") + " - "
                + value(row, "customer_name") + ", trạng thái " + statusValue(row, "status")
                + ", SL " + value(row, "received_qty") + "/" + value(row, "expected_qty")
                + ", lý do " + value(row, "reason"));
    }

    private String locationReply(List<?> list) {
        return "Tìm thấy " + list.size() + " vị trí: " + joinRows(limit(list, 8), row ->
                value(row, "warehouse_code") + "/" + value(row, "code")
                        + " zone " + value(row, "zone")
                        + ", trạng thái " + statusValue(row, "status"));
    }

    private String auditLogReply(List<?> list) {
        return "Có " + list.size() + " dòng audit log gần nhất: " + joinRows(limit(list, 8), row ->
                value(row, "created_at") + " - " + value(row, "module") + "/"
                        + value(row, "action_type") + ": " + value(row, "action")
                        + ", đối tượng " + value(row, "entity_type") + " " + value(row, "entity_name"));
    }

    private String aiAuditLogReply(List<?> list) {
        return "Có " + list.size() + " dòng AI audit gần nhất: " + joinRows(limit(list, 8), row ->
                value(row, "created_at") + " - câu hỏi \"" + value(row, "question")
                        + "\", rows " + value(row, "rows_returned")
                        + ", latency " + value(row, "latency_ms") + "ms"
                        + ("N/A".equals(value(row, "execution_error")) ? "" : ", lỗi " + value(row, "execution_error")));
    }

    private String dailyTasksReply(Map<?, ?> map) {
        return "📅 **Nhiệm vụ vận hành cần lưu ý trong ngày:**\n" +
               "- **Putaway chờ xếp kệ:** **" + formatNumber(longValue(nestedValue(map, "pending_putaway", "total"))) + "** task\n" +
               "- **Picking đang chờ:** **" + formatNumber(longValue(nestedValue(map, "pending_picking", "total"))) + "** đơn hàng\n" +
               "- **Cycle count đang mở:** **" + formatNumber(longValue(nestedValue(map, "active_cycle_counts", "total"))) + "** đợt kiểm kê\n" +
               "- **Lô hàng sắp hết hạn (trong 7 ngày):** **" + formatNumber(longValue(nestedValue(map, "near_expiry_7_days", "total"))) + "** lô\n" +
               "- **Yêu cầu trả hàng (RMA) chờ xử lý:** **" + formatNumber(longValue(nestedValue(map, "pending_rma", "total"))) + "** yêu cầu.";
    }

    private String reportSummaryReply(Map<?, ?> map) {
        return "📊 **Tổng quan tình hình vận hành hệ thống:**\n" +
               "- **Kho hoạt động:** **" + formatNumber(longValue(nestedValue(map, "warehouses", "active"))) + "** kho đang hoạt động\n" +
               "- **Tổng tồn kho hiện có:** **" + formatNumber(longValue(nestedValue(map, "stock", "qty_on_hand"))) + "** đơn vị\n" +
               "- **Tồn kho đang giữ chỗ (reserved):** **" + formatNumber(longValue(nestedValue(map, "stock", "qty_reserved"))) + "** đơn vị\n" +
               "- **Putaway đang chờ xử lý:** **" + formatNumber(longValue(nestedValue(map, "pending_putaway", "total"))) + "** task\n" +
               "- **Đơn xuất ưu tiên cần xử lý:** **" + formatNumber(longValue(nestedValue(map, "priority_outbound", "total"))) + "** đơn.";
    }

    private String flowReportReply(Map<?, ?> map) {
        return "📈 **Báo cáo biến động nhập - xuất trong 7 ngày qua:**\n" +
               "📥 **Hoạt động nhập kho (Inbound):**\n" +
               "  - Số phiếu nhập: **" + formatNumber(longValue(nestedValue(map, "inbound", "receipts"))) + "** phiếu\n" +
               "  - Tổng số lượng nhập: **" + formatNumber(longValue(nestedValue(map, "inbound", "received_qty"))) + "** đơn vị\n" +
               "📤 **Hoạt động xuất kho (Outbound):**\n" +
               "  - Số đơn bán: **" + formatNumber(longValue(nestedValue(map, "outbound", "sales_orders"))) + "** đơn\n" +
               "  - Tổng số lượng đặt: **" + formatNumber(longValue(nestedValue(map, "outbound", "ordered_qty"))) + "** đơn vị\n" +
               "  - Đã thực xuất giao: **" + formatNumber(longValue(nestedValue(map, "outbound", "shipped_qty"))) + "** đơn vị\n" +
               "🔄 **Biến động dịch chuyển tồn kho:** **" + formatNumber(longValue(nestedValue(map, "stock_movements", "movements"))) + "** lượt giao dịch.";
    }

    private String inboundReportReply(Map<?, ?> map) {
        return "Báo cáo nhập kho: phiếu nhập " + nestedValue(map, "summary", "receipts")
                + ", PO " + nestedValue(map, "summary", "purchase_orders")
                + ", số lượng nhận " + nestedValue(map, "summary", "received_qty")
                + ". Top NCC: " + joinRowsFromMapList(map, "top_suppliers", row ->
                value(row, "supplier_name") + " nhận " + value(row, "received_qty"));
    }

    private String outboundReportReply(Map<?, ?> map, AiIntentResult route) {
        String query = routeQuery(route);
        if (map.containsKey("warehouse_code")
                && containsAny(query, "xuat bao nhieu don", "xuat may don", "xu ly bao nhieu don",
                "don xuat hom nay", "hom nay xuat")) {
            String scope = "TODAY".equalsIgnoreCase(value(map, "dateRange")) ? "hôm nay" : "trong phạm vi bạn hỏi";
            return value(map, "warehouse_code") + " đã xử lý "
                    + formatNumber(nestedValue(map, "summary", "sales_orders")) + " đơn xuất " + scope + ".";
        }
        return "Báo cáo xuất kho: đơn bán " + nestedValue(map, "summary", "sales_orders")
                + ", số lượng đặt " + nestedValue(map, "summary", "ordered_qty")
                + ", đã giao " + nestedValue(map, "summary", "shipped_qty")
                + ". Theo trạng thái: " + joinRowsFromMapList(map, "by_status", row ->
                value(row, "status") + " " + value(row, "sales_orders") + " đơn");
    }

    private String monthlyReportReply(Map<?, ?> map) {
        return "Báo cáo tháng này: tồn hiện có " + nestedValue(map, "stock", "qty_on_hand")
                + ", khả dụng " + nestedValue(map, "stock", "qty_available")
                + "; nhập " + nestedValue(map, "inbound", "received_qty")
                + "; xuất đặt " + nestedValue(map, "outbound", "ordered_qty")
                + ", đã giao " + nestedValue(map, "outbound", "shipped_qty")
                + "; stock movement " + nestedValue(map, "movements", "movements") + ".";
    }

    private String inventoryValueReply(Map<?, ?> map) {
        String totalValue = formatCurrency(nestedValue(map, "summary", "inventory_value"));
        String totalQty = formatNumber(longValue(nestedValue(map, "summary", "qty_on_hand")));
        
        StringBuilder sb = new StringBuilder();
        sb.append("💰 **Tổng giá trị tồn kho ước tính hiện tại:** **").append(totalValue).append("**\n")
          .append("📦 **Tổng số lượng sản phẩm:** **").append(totalQty).append("** đơn vị\n\n")
          .append("📊 **Nhóm sản phẩm có giá trị tồn kho cao nhất:**\n");
          
        Object topProducts = map.get("top_products");
        if (topProducts instanceof List<?> topList && !topList.isEmpty()) {
            sb.append(joinRowsAsBulletList(limit(topList, 5), row -> 
                value(row, "product_name") + " (SKU: **" + value(row, "sku") + "**) ~ **" + formatCurrency(value(row, "inventory_value")) + "**"
            ));
        } else {
            sb.append("- *Không có dữ liệu chi tiết*");
        }
        return sb.toString();
    }

    private String inventoryValueByWarehouseReply(List<?> list) {
        if (list.size() >= 2) {
            Object first = list.get(0);
            Object second = list.get(1);
            return "📊 **So sánh giá trị tồn kho giữa các kho:**\n" +
                   "- **" + value(first, "warehouse_code") + "**: **" + formatCurrency(value(first, "inventory_value")) + "** (tồn **" + formatNumber(longValue(first, "qty_on_hand")) + "** đơn vị)\n" +
                   "- **" + value(second, "warehouse_code") + "**: **" + formatCurrency(value(second, "inventory_value")) + "** (tồn **" + formatNumber(longValue(second, "qty_on_hand")) + "** đơn vị)\n\n" +
                   "👉 Kho có giá trị tồn cao hơn: **" + value(first, "warehouse_code") + "**.";
        }
        Object row = list.get(0);
        return "📊 **Giá trị tồn kho tại kho " + value(row, "warehouse_code") + ":**\n" +
               "- **Tổng giá trị:** **" + formatCurrency(value(row, "inventory_value")) + "**\n" +
               "- **Tổng số lượng tồn:** **" + formatNumber(longValue(row, "qty_on_hand")) + "** đơn vị.";
    }

    private String inactiveProductWithStockReply(Map<?, ?> map) {
        String count = formatNumber(longValue(nestedValue(map, "summary", "product_count")));
        String totalQty = formatNumber(longValue(nestedValue(map, "summary", "qty_on_hand")));
        
        StringBuilder sb = new StringBuilder();
        sb.append("🚫 **Phát hiện ").append(count).append(" sản phẩm đã ngừng kinh doanh (không ACTIVE) nhưng vẫn còn tồn kho:**\n")
          .append("- **Tổng số lượng tồn:** **").append(totalQty).append("** đơn vị\n\n")
          .append("📋 **Một số sản phẩm tiêu biểu:**\n");
          
        Object items = map.get("items");
        if (items instanceof List<?> itemList && !itemList.isEmpty()) {
            sb.append(joinRowsAsBulletList(limit(itemList, 5), row -> 
                "**" + value(row, "sku") + "** - " + value(row, "product_name") + " " +
                "(Trạng thái: `" + value(row, "status") + "`, Tồn: **" + formatNumber(longValue(row, "qty_on_hand")) + "** đơn vị)"
            ));
        } else {
            sb.append("- *Không có danh sách chi tiết*");
        }
        return sb.toString();
    }

    private String longestExpiryReply(List<?> list) {
        Object first = list.get(0);
        return "📅 **Lô hàng có hạn sử dụng dài nhất còn lại:**\n" +
               "- **Mã lô (Lot Number):** `" + value(first, "lot_number") + "`\n" +
               "- **Sản phẩm:** SKU **" + value(first, "sku") + "** - " + value(first, "product_name") + "\n" +
               "- **Hạn sử dụng:** `" + value(first, "expiry_date") + "` (còn khoảng **" + formatNumber(value(first, "days_remaining")) + "** ngày)\n" +
               "- **Vị trí lưu trữ:** Kho **" + value(first, "warehouse_code") + "**\n" +
               "- **Số lượng tồn:** **" + formatNumber(longValue(first, "qty_on_hand")) + "** đơn vị.";
    }

    private String inboundProductQtyReply(Map<?, ?> map) {
        return "Số lượng nhập kho trong phạm vi bạn hỏi là "
                + nestedValue(map, "summary", "received_qty") + " đơn vị trên "
                + nestedValue(map, "summary", "receipts") + " phiếu nhập. Chi tiết: "
                + joinRowsFromMapList(map, "items", row -> value(row, "receipt_number")
                + " - " + value(row, "product_name") + " SL " + value(row, "received_qty"));
    }

    private String inboundPendingPutawayReply(List<?> list) {
        List<?> displayed = limit(list, 10);
        String prefix = "📥 **Có " + list.size() + " dòng/phiếu nhập đang chờ xếp hàng lên kệ (Putaway):**\n\n";
        String items = joinRowsAsBulletList(displayed, row -> 
            "Phiếu **" + value(row, "po_number") + "** / GRN `" + value(row, "receipt_number") + "`\n" +
            "  - Sản phẩm: " + value(row, "product_name") + "\n" +
            "  - Số lượng: **" + formatNumber(longValue(row, "qty_to_putaway")) + "** đơn vị\n" +
            "  - Trạng thái: `" + statusLabel(value(row, "putaway_status")) + "`"
        );
        String suffix = list.size() > displayed.size() 
            ? "\n\n*(Chỉ hiển thị " + displayed.size() + " dòng đầu tiên)*" 
            : "";
        return prefix + items + suffix;
    }

    private String inboundAvgDailyReply(Map<?, ?> map) {
        return "Trung bình " + value(map, "avg_receipts_per_day")
                + " lô/phiếu nhập mỗi ngày trong " + value(map, "days")
                + " ngày gần đây; trung bình số lượng nhập " + value(map, "avg_qty_per_day")
                + " đơn vị/ngày.";
    }

    private String outboundTotalQtyReply(Map<?, ?> map) {
        return "Tổng xuất kho trong phạm vi bạn hỏi: " + value(map, "shipped_qty")
                + " đơn vị đã giao trên " + value(map, "ordered_qty")
                + " đơn vị đặt, gồm " + value(map, "sales_orders") + " đơn xuất.";
    }

    private String outboundDelayedReply(List<?> list) {
        List<?> displayed = limit(list, 10);
        String prefix = "🚨 **Cảnh báo: Có " + list.size() + " đơn xuất kho đang trễ hoặc có nguy cơ trễ hạn giao hàng!**\n\n" +
                       "📋 **Danh sách các đơn hàng cần xử lý gấp:**\n";
        String items = joinRowsAsBulletList(displayed, row -> 
            "Đơn **" + value(row, "so_number") + "** - " + value(row, "customer_name") + " " +
            "(Trạng thái: `" + statusValue(row, "status") + "`, Tạo lúc: `" + formatDateTime(value(row, "created_at")) + "`)"
        );
        String suffix = list.size() > displayed.size() 
            ? "\n\n*(Chỉ hiển thị " + displayed.size() + " đơn hàng ưu tiên nhất)*" 
            : "";
        return prefix + items + suffix;
    }

    private String stockFastestDecreaseReply(List<?> list) {
        Object first = list.get(0);
        return "Sản phẩm giảm tồn nhanh nhất trong 7 ngày qua là " + value(first, "sku")
                + " - " + value(first, "product_name") + " tại " + value(first, "warehouse_code")
                + ", giảm " + value(first, "decreased_qty") + " đơn vị. Top: "
                + joinRows(limit(list, 5), row -> value(row, "sku") + " giảm "
                + value(row, "decreased_qty"));
    }

    private String pickLocationUsageReply(List<?> list) {
        Object first = list.get(0);
        return "Vị trí lấy hàng được dùng nhiều nhất là " + value(first, "warehouse_code")
                + "/" + value(first, "location_code") + " với " + value(first, "pick_lines")
                + " dòng pick, tổng cần pick " + value(first, "qty_to_pick") + " đơn vị.";
    }

    private String outboundDelayReasonReply(List<?> list) {
        Object first = list.get(0);
        return "Lý do xuất chậm/lỗi xuất được ghi nhận nhiều nhất là: "
                + value(first, "reason") + " (" + value(first, "issue_count")
                + " lần). Các lý do khác: " + joinRows(limit(list, 5), row ->
                value(row, "reason") + " " + value(row, "issue_count") + " lần");
    }

    private String outboundShortageReply(Map<?, ?> map) {
        return "Có " + nestedValue(map, "summary", "orders")
                + " đơn xuất có dòng picking chưa đủ hàng, tổng thiếu/chưa pick "
                + nestedValue(map, "summary", "shortage_qty") + " đơn vị. Một số dòng: "
                + joinRowsFromMapList(map, "items", row -> value(row, "so_number")
                + " - " + value(row, "sku") + ", thiếu " + value(row, "shortage_qty"));
    }

    private String cycleCountRecountSkusReply(List<?> list) {
        return "Các SKU cần kiểm kê lại sau sai lệch: " + joinRows(limit(list, 8), row ->
                value(row, "sku") + " - " + value(row, "product_name")
                        + ", chênh lệch tuyệt đối " + value(row, "total_abs_discrepancy")
                        + ", " + value(row, "variance_lines") + " dòng lệch");
    }

    private String outboundCancelledOrReturnedReply(Map<?, ?> map) {
        String cancelledOrders = formatNumber(longValue(nestedValue(map, "cancelled", "orders")));
        String cancelledQty = formatNumber(longValue(nestedValue(map, "cancelled", "ordered_qty")));
        String returnedOrders = formatNumber(longValue(nestedValue(map, "returned", "rma_orders")));
        String returnedQty = formatNumber(longValue(nestedValue(map, "returned", "received_qty")));
        
        return "📉 **Thống kê đơn xuất bị hủy & trả lại (RMA):**\n" +
               "- **Đơn xuất bị hủy:** **" + cancelledOrders + "** đơn (Tổng số lượng: **" + cancelledQty + "** đơn vị)\n" +
               "- **Yêu cầu trả hàng (RMA) trong tháng:** **" + returnedOrders + "** yêu cầu (Tổng số lượng đã nhận lại: **" + returnedQty + "** đơn vị).";
    }

    private String pickingCompletedCountReply(Map<?, ?> map) {
        return "Hoàn thành picking: " + value(map, "completed_orders")
                + " đơn, " + value(map, "completed_lines")
                + " dòng, tổng đã pick " + value(map, "qty_picked") + " đơn vị.";
    }

    private String pickingCompletionRateReply(Map<?, ?> map) {
        return "Tỷ lệ hoàn thành picking là " + value(map, "line_completion_rate")
                + "% theo dòng task và " + value(map, "qty_completion_rate")
                + "% theo số lượng đã pick.";
    }

    private String pickingStockCheckReply(Map<?, ?> map) {
        return "SKU " + value(map, "sku") + " hiện khả dụng " + value(map, "qty_available")
                + " đơn vị; nhu cầu pick " + value(map, "requested_qty")
                + " đơn vị, kết luận: " + ("true".equalsIgnoreCase(value(map, "enough")) ? "đủ hàng." : "không đủ hàng.");
    }

    private String cycleCountSummaryReply(Map<?, ?> map) {
        return "Kết quả kiểm kê tháng này: thực tế " + nestedValue(map, "summary", "counted_qty")
                + ", hệ thống " + nestedValue(map, "summary", "system_qty")
                + ", chênh lệch ròng " + nestedValue(map, "summary", "net_discrepancy")
                + "; thừa " + nestedValue(map, "summary", "over_qty")
                + ", thiếu " + nestedValue(map, "summary", "under_qty") + ".";
    }

    private String cycleCountCompletionRateReply(Map<?, ?> map) {
        return "Tỷ lệ hoàn thành kiểm kê hiện đạt " + value(map, "completion_rate")
                + "%, đã hoàn thành " + value(map, "completed_counts")
                + " trên tổng " + value(map, "total_counts") + " phiếu.";
    }

    private String rmaRateReply(Map<?, ?> map) {
        return "Tỷ lệ RMA được chấp nhận hiện là " + value(map, "accepted_rate")
                + "%: " + value(map, "accepted_rma") + " / " + value(map, "total_rma")
                + " RMA; bị từ chối/hủy " + value(map, "rejected_rma") + ".";
    }

    private String rmaProcessingAvgReply(Map<?, ?> map) {
        return "Thời gian xử lý RMA trung bình là " + value(map, "avg_days")
                + " ngày trên " + value(map, "completed_rma") + " RMA đã hoàn tất.";
    }

    private String rmaValueReply(Map<?, ?> map) {
        return "Tổng giá trị hàng RMA trong phạm vi bạn hỏi khoảng " + value(map, "rma_value")
                + ", gồm " + value(map, "total_rma") + " RMA, số lượng nhận "
                + value(map, "received_qty") + " đơn vị.";
    }

    private String taskCompletionRateReply(Map<?, ?> map) {
        return "Tỷ lệ hoàn thành picking là " + nestedValue(map, "picking", "line_completion_rate")
                + "% theo dòng task; putaway là " + nestedValue(map, "putaway", "completion_rate")
                + "%, đã hoàn thành " + nestedValue(map, "putaway", "completed_tasks")
                + " / " + nestedValue(map, "putaway", "total_tasks") + " task.";
    }

    private String employeeOperationProductivityReply(Map<?, ?> map) {
        return "Hiệu suất nhân viên: picking - " + joinRowsFromMapList(map, "picking", row ->
                value(row, "assignee") + " pick " + value(row, "qty_picked") + "/"
                        + value(row, "qty_to_pick"))
                + "; putaway - " + joinRowsFromMapList(map, "putaway", row ->
                value(row, "assignee") + " hoàn thành " + value(row, "completed_lines")
                        + "/" + value(row, "task_lines") + " task");
    }

    private String overdueTasksReply(Map<?, ?> map) {
        return "Task quá hạn ước tính: picking " + nestedValue(map, "picking", "task_lines")
                + " dòng trên " + nestedValue(map, "picking", "orders")
                + " đơn, còn " + nestedValue(map, "picking", "remaining_qty")
                + " đơn vị; putaway " + nestedValue(map, "putaway", "task_lines")
                + " task, số lượng " + nestedValue(map, "putaway", "qty_to_putaway") + ".";
    }

    private String operationIssueReportReply(Map<?, ?> map) {
        return "Lỗi/sự cố vận hành hôm nay: " + joinRowsFromMapList(map, "audit_issues", row ->
                value(row, "module") + "/" + value(row, "action_type")
                        + " - " + value(row, "reason") + ": " + value(row, "issue_count") + " lần")
                + ". " + value(map, "message");
    }

    private String monthOverMonthFlowReply(Map<?, ?> map) {
        return "So với tháng trước, tháng này nhập " + nestedValue(map, "current_inbound", "received_qty")
                + " (chênh " + nestedValue(map, "delta", "received_qty") + "), xuất đặt "
                + nestedValue(map, "current_outbound", "ordered_qty") + " (chênh "
                + nestedValue(map, "delta", "ordered_qty") + "), đã giao "
                + nestedValue(map, "current_outbound", "shipped_qty") + " (chênh "
                + nestedValue(map, "delta", "shipped_qty") + ").";
    }

    private String globalSearchReply(Map<?, ?> map) {
        return "Kết quả tìm kiếm: sản phẩm " + listSize(map, "products")
                + ", kho " + listSize(map, "warehouses")
                + ", NCC " + listSize(map, "suppliers")
                + ", khách hàng " + listSize(map, "customers")
                + ", PO " + listSize(map, "purchase_orders")
                + ", SO " + listSize(map, "sales_orders") + ".";
    }

    private String fulfillmentRateReply(Map<?, ?> map) {
        return "Tỷ lệ fulfillment hiện là " + value(map, "fulfillment_rate") + "%: đã giao "
                + value(map, "shipped_qty") + " / " + value(map, "ordered_qty")
                + " đơn vị trên " + value(map, "sales_orders") + " đơn xuất.";
    }

    private String myTasksReply(Map<?, ?> map) {
        return "Việc được giao hiện tại: putaway " + listSize(map, "putaway")
                + ", picking " + listSize(map, "picking")
                + ", kiểm kê " + listSize(map, "cycle_counts") + ". "
                + "Picking: " + joinRowsFromMapList(map, "picking", row -> value(row, "so_number")
                + " - " + value(row, "sku") + ", cần " + value(row, "qty_to_pick")
                + ", đã pick " + value(row, "qty_picked"))
                + ". Putaway: " + joinRowsFromMapList(map, "putaway", row -> value(row, "sku")
                + " - " + value(row, "product_name") + ", SL " + value(row, "qty_to_putaway")
                + ", trạng thái " + value(row, "status")) + ".";
    }

    private String notificationReply(List<?> list) {
        return "Bạn có " + list.size() + " thông báo gần nhất: " + joinRows(limit(list, 6), row ->
                value(row, "severity") + " - " + value(row, "title")
                        + " (" + value(row, "created_at") + ")");
    }

    private String categoryReply(List<?> list) {
        List<?> displayed = limit(list, 8);
        String prefix = displayed.size() < list.size()
                ? "Có " + list.size() + " danh mục. Hiển thị " + displayed.size() + " danh mục đầu tiên:"
                : "Có " + list.size() + " danh mục:";
        String suffix = displayed.size() < list.size()
                ? "\nCòn " + (list.size() - displayed.size()) + " danh mục khác chưa hiển thị."
                : "";
        return prefix + "\n" + joinRowsAsBulletList(displayed, row ->
                value(row, "code") + " - " + value(row, "name")
                        + ", sản phẩm " + value(row, "product_count")) + suffix;
    }

    private String productByCategoryReply(List<?> list) {
        List<?> displayed = limit(list, 8);
        String prefix = displayed.size() < list.size()
                ? "Tìm thấy " + list.size() + " sản phẩm trong danh mục. Hiển thị "
                        + displayed.size() + " sản phẩm đầu tiên:"
                : "Tìm thấy " + list.size() + " sản phẩm trong danh mục:";
        String suffix = displayed.size() < list.size()
                ? "\nCòn " + (list.size() - displayed.size()) + " sản phẩm khác chưa hiển thị."
                : "";
        return prefix + "\n" + joinRowsAsBulletList(displayed, row ->
                value(row, "sku") + " - " + value(row, "product_name")
                        + ", tồn " + value(row, "qty_on_hand")) + suffix;
    }

    private String stockByLocationReply(List<?> list) {
        return "Vị trí đang chứa: " + joinRows(limit(list, 8), row ->
                value(row, "warehouse_code") + "/" + value(row, "location_code")
                        + " - " + value(row, "sku") + " " + value(row, "product_name")
                        + ", lot " + value(row, "lot_number")
                        + ", tồn " + value(row, "qty_on_hand"));
    }

    private String stockByLotReply(List<?> list) {
        long total = sumLong(list, "qty_on_hand");
        return "Lô này còn tổng " + formatNumber(total) + " đơn vị: " + joinRows(limit(list, 8), row ->
                value(row, "sku") + " tại " + value(row, "warehouse_code") + "/" + value(row, "location_code")
                        + ", tồn " + value(row, "qty_on_hand")
                        + ", hết hạn " + value(row, "expiry_date"));
    }

    private String deadStockReply(List<?> list) {
        return "Có " + list.size() + " SKU thuộc nhóm hàng chết/tồn lâu: " + joinRows(limit(list, 6), row ->
                value(row, "sku") + " - " + value(row, "product_name")
                        + ", tồn " + value(row, "qty_on_hand")
                        + ", giao dịch cuối " + value(row, "last_movement_at"));
    }

    private String stockAtRiskReply(List<?> list) {
        return "Có " + list.size() + " lô tồn kho rủi ro: " + joinRows(limit(list, 6), row ->
                value(row, "sku") + " lot " + value(row, "lot_number")
                        + ", hết hạn " + value(row, "expiry_date")
                        + ", tồn " + value(row, "qty_on_hand"));
    }

    private String reorderSuggestionReply(List<?> list) {
        return "Gợi ý nhập bổ sung: " + joinRows(limit(list, 8), row ->
                value(row, "sku") + " - " + value(row, "product_name")
                        + ", khả dụng " + value(row, "qty_available")
                        + ", tối thiểu " + value(row, "min_stock_qty")
                        + ", đề xuất " + value(row, "suggested_qty"));
    }

    private String topCustomersReply(List<?> list) {
        return "Top khách hàng: " + joinRows(limit(list, 8), row ->
                value(row, "customer_name") + ": " + value(row, "order_count")
                        + " đơn, giá trị đã giao " + value(row, "shipped_value"));
    }

    private String warehouseCapacityReply(List<?> list) {
        return "Công suất kho: " + joinRows(limit(list, 8), row ->
                value(row, "warehouse_code") + " dùng " + value(row, "capacity_used_pct")
                        + "% (" + value(row, "occupied_locations") + "/"
                        + value(row, "total_locations") + " vị trí).");
    }

    private String pickingProductivityReply(List<?> list) {
        return "Năng suất picking: " + joinRows(limit(list, 8), row ->
                value(row, "assignee") + " đã pick " + value(row, "qty_picked")
                        + "/" + value(row, "qty_to_pick") + " đơn vị, "
                        + value(row, "completed_lines") + " dòng hoàn tất.");
    }

    private String supplierPerformanceReply(List<?> list) {
        return "Hiệu suất NCC: " + joinRows(limit(list, 8), row ->
                value(row, "supplier_code") + " - " + value(row, "supplier_name")
                        + ": " + value(row, "purchase_orders") + " PO, hoàn tất "
                        + value(row, "completed_orders") + ", đúng hạn "
                        + value(row, "on_time_orders"));
    }

    private String userLookupReply(List<?> list) {
        return "Tìm thấy " + list.size() + " người dùng: " + joinRows(limit(list, 8), row ->
                value(row, "username") + " - " + value(row, "full_name")
                        + ", vai trò " + value(row, "roles")
                        + ", kho " + value(row, "warehouses"));
    }

    private String roleListReply(List<?> list) {
        return "Hệ thống có " + list.size() + " vai trò: " + joinRows(limit(list, 10), row ->
                value(row, "code") + " - " + value(row, "name")
                        + " (" + value(row, "user_count") + " user)");
    }

    private String routeQuery(AiIntentResult route) {
        return normalize(routeParam(route, "query"));
    }

    private String routeParam(AiIntentResult route, String... keys) {
        if (route == null) {
            return null;
        }
        for (String key : keys) {
            Object value = route.safeParameters().get(key);
            if (value != null && !"null".equalsIgnoreCase(String.valueOf(value))) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private boolean containsAny(String text, String... candidates) {
        if (text == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String productLabel(Object row) {
        String sku = value(row, "sku");
        String name = value(row, "product_name");
        if (!"N/A".equals(sku) && !"N/A".equals(name)) {
            return "SKU " + sku + " - " + name;
        }
        return !"N/A".equals(name) ? name : "Sản phẩm";
    }

    private Map<String, long[]> stockByWarehouse(List<?> rows) {
        Map<String, long[]> grouped = new LinkedHashMap<>();
        for (Object row : rows) {
            String code = value(row, "warehouse_code");
            long[] sums = grouped.computeIfAbsent(code, ignored -> new long[3]);
            sums[0] += longValue(row, "qty_on_hand");
            sums[1] += longValue(row, "qty_reserved");
            sums[2] += longValue(row, "qty_available");
        }
        return grouped;
    }

    private String singleWarehouseCode(Map<String, long[]> byWarehouse) {
        List<String> codes = byWarehouse.keySet().stream()
                .filter(code -> !"N/A".equals(code))
                .toList();
        return codes.size() == 1 ? codes.get(0) : null;
    }

    private long sumLong(List<?> rows, String key) {
        return rows.stream().mapToLong(row -> longValue(row, key)).sum();
    }

    private long longValue(Object row, String key) {
        if (row instanceof Map<?, ?> map) {
            return longValue(map.get(key));
        }
        return 0;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return Math.round(number.doubleValue());
        }
        if (value == null) {
            return 0;
        }
        try {
            return new BigDecimal(String.valueOf(value).replace(",", "")).longValue();
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String firstNonBlank(List<?> rows, String key) {
        return rows.stream()
                .map(row -> value(row, key))
                .filter(value -> value != null && !value.isBlank() && !"N/A".equals(value))
                .findFirst()
                .orElse("N/A");
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "N/A";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PICKING" -> "Picking In Progress";
            case "PENDING" -> "Pending";
            case "IN_PROGRESS" -> "In Progress";
            case "ASSIGNED" -> "Assigned";
            case "PICKED" -> "Picked";
            case "RECEIVED" -> "Received";
            case "PUTAWAY_IN_PROGRESS" -> "Putaway In Progress";
            case "COMPLETED" -> "Completed";
            case "APPROVED" -> "Approved";
            case "CANCELLED" -> "Cancelled";
            case "SHIPPED" -> "Shipped";
            case "PACKED" -> "Packed";
            default -> status;
        };
    }

    private String statusValue(Object row, String key) {
        return statusLabel(value(row, key));
    }

    private String returnStatusLabel(String status) {
        if (status == null) {
            return "ở trạng thái N/A";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "RECEIVED", "QC_PENDING", "PENDING_QC" -> "chờ QC approval";
            case "REQUESTED" -> "chờ tiếp nhận";
            case "APPROVED" -> "đã được duyệt";
            case "COMPLETED", "CLOSED" -> "đã hoàn tất";
            default -> "ở trạng thái " + statusLabel(status);
        };
    }

    private String formatNumber(String value) {
        return formatNumber(longValue(value));
    }

    private String formatNumber(long value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    private String formatCurrency(Object value) {
        if (value == null) {
            return "0 VNĐ";
        }
        long amount = longValue(value);
        return formatNumber(amount) + " VNĐ";
    }

    private String formatDateTime(String val) {
        if (val == null || "N/A".equals(val) || val.isBlank()) {
            return "N/A";
        }
        if (val.length() >= 19) {
            String clean = val.substring(0, 19).replace('T', ' ');
            try {
                String[] parts = clean.split(" ");
                if (parts.length == 2) {
                    String datePart = parts[0];
                    String timePart = parts[1];
                    String[] dateSplits = datePart.split("-");
                    if (dateSplits.length == 3) {
                        return timePart + " " + dateSplits[2] + "/" + dateSplits[1] + "/" + dateSplits[0];
                    }
                }
            } catch (Exception ignored) {}
            return clean;
        }
        return val;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        return normalized
                .replaceAll("\\bko\\b", "khong")
                .replaceAll("\\bk\\b", "khong");
    }

    private String joinRows(List<?> rows, java.util.function.Function<Object, String> formatter) {
        return rows.stream()
                .map(formatter)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    private String joinRowsAsBulletList(List<?> rows, java.util.function.Function<Object, String> formatter) {
        return rows.stream()
                .map(row -> "- " + formatter.apply(row))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private String joinRowsFromMapList(Map<?, ?> map, String key, java.util.function.Function<Object, String> formatter) {
        Object value = map.get(key);
        if (!(value instanceof List<?> rows) || rows.isEmpty()) {
            return "không có dữ liệu";
        }
        return joinRows(limit(rows, 5), formatter);
    }

    private int listSize(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof List<?> rows ? rows.size() : 0;
    }

    private List<?> limit(List<?> rows, int limit) {
        return rows.size() <= limit ? rows : rows.subList(0, limit);
    }

    private Map<?, ?> firstMap(List<?> rows) {
        return rows.isEmpty() || !(rows.get(0) instanceof Map<?, ?> map) ? Map.of() : map;
    }

    private boolean hasKey(Map<?, ?> map, String key) {
        return map.containsKey(key);
    }

    private String value(Object row, String key) {
        if (row instanceof Map<?, ?> map) {
            return value(map, key);
        }
        return "";
    }

    private String value(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "N/A" : String.valueOf(value);
    }

    private String nestedValue(Map<?, ?> map, String parent, String child) {
        Object nested = map.get(parent);
        if (nested instanceof Map<?, ?> nestedMap) {
            return value(nestedMap, child);
        }
        return "N/A";
    }

    private String activeText(String value) {
        return "true".equalsIgnoreCase(value) ? "đang hoạt động" : "ngừng hoạt động";
    }

    // Tạo prompt để model viết câu trả lời từ JSON của tool.
    private String buildAnswerPrompt(String userMessage, AiIntentResult route, AiToolResult toolResult,
            List<Map<String, String>> history) {
        return """
                <|im_start|>system
                Bạn là trợ lý AI vận hành kho StockMaster-WMS.
                Trả lời bằng tiếng Việt, ngắn gọn, chuyên nghiệp, ưu tiên số liệu cụ thể.

                Quy tắc bắt buộc:
                - Chỉ dùng dữ liệu trong Tool result JSON.
                - Không tự bịa SKU, tên sản phẩm, kho, số lượng, trạng thái, ngày tháng.
                - Không nói về intent, tool, JSON, SQL hoặc chi tiết kỹ thuật.
                - Nếu dữ liệu là danh sách, tóm tắt số lượng dòng và liệt kê các mục quan trọng nhất.
                - Nếu dữ liệu rỗng, nói rõ chưa tìm thấy dữ liệu phù hợp.
                - Nếu câu hỏi cần hành động nghiệp vụ, chỉ gợi ý bước kiểm tra tiếp theo, không tự xác nhận đã thao tác.
                <|im_end|>
                <|im_start|>user
                History JSON: %s
                Câu hỏi hiện tại: %s
                Intent: %s
                Parameters JSON: %s
                Tool: %s
                Tool result JSON: %s
                <|im_end|>
                <|im_start|>assistant
                """.formatted(
                toJson(compactHistory(history)),
                userMessage,
                route == null ? "UNSUPPORTED" : route.getIntent(),
                toJson(route == null ? Map.of() : route.safeParameters()),
                toolResult == null ? "none" : toolResult.toolName(),
                toJson(compactToolResultData(toolResult == null ? null : toolResult.data()))
        );
    }

    // Chuyển object sang JSON để đưa vào prompt.
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Cannot serialize AI prompt payload", e);
            return String.valueOf(value);
        }
    }

    private List<Map<String, String>> compactHistory(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int startIndex = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        return history.subList(startIndex, history.size());
    }

    private Object compactToolResultData(Object data) {
        if (data instanceof List<?> list && list.size() > MAX_TOOL_LIST_ITEMS) {
            return list.subList(0, MAX_TOOL_LIST_ITEMS);
        }
        if (data instanceof String text && text.length() > MAX_TOOL_TEXT_LENGTH) {
            return text.substring(0, MAX_TOOL_TEXT_LENGTH) + "...";
        }
        return data;
    }
}
