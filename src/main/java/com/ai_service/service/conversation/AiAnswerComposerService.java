package com.ai_service.service.conversation;

import com.ai_service.client.AiTextClient;
import com.ai_service.context.AiQueryContext;
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
import java.util.UUID;
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
        return compose(userMessage, route, toolResult, history,
                AiQueryContext.from(userMessage, route, toolResult, estimateRows(toolResult)));
    }

    public String compose(String userMessage, AiIntentResult route, AiToolResult toolResult,
            List<Map<String, String>> history, AiQueryContext context) {
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
        String answer = aiTextClient.generateAnswer(buildAnswerPrompt(userMessage, route, toolResult, history, context));
        log.info("AI compose mode=selected-model done intent={} tool={} outputChars={} durationMs={}",
                route == null ? "null" : route.getIntent(),
                toolResult == null ? "null" : toolResult.toolName(),
                answer == null ? 0 : answer.length(), System.currentTimeMillis() - start);
        return answer;
    }

    // Tạo câu trả lời AI dạng stream từ route và tool result.
    public void composeStream(String userMessage, AiIntentResult route, AiToolResult toolResult,
            List<Map<String, String>> history, Consumer<String> fragmentConsumer, java.util.function.Supplier<Boolean> isCancelled) {
        AiQueryContext context = AiQueryContext.from(userMessage, route, toolResult, estimateRows(toolResult));
        composeStream(userMessage, route, toolResult, history, context, fragmentConsumer, isCancelled);
    }

    public void composeStream(String userMessage, AiIntentResult route, AiToolResult toolResult,
            List<Map<String, String>> history, AiQueryContext context, Consumer<String> fragmentConsumer,
            java.util.function.Supplier<Boolean> isCancelled) {
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
        aiTextClient.generateAnswerStream(buildAnswerPrompt(userMessage, route, toolResult, history, context), fragmentConsumer, isCancelled);
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
            if (toolResult.missingParams() != null && !toolResult.missingParams().isEmpty()) {
                return missingParameterReply(route, toolResult);
            }
            if ((route != null && route.getIntent() == AiIntent.AMBIGUOUS)
                    || toolResult.toolName() != null && toolResult.toolName().toLowerCase(Locale.ROOT).contains("clarification")) {
                return clarificationReply(route, toolResult);
            }
            return toolResult.message();
        }
        Object data = toolResult.data();
        if (data instanceof List<?> list && list.isEmpty()) {
            return switch (route.getIntent()) {
                case LOW_STOCK -> "Hiện chưa ghi nhận sản phẩm nào dưới mức tồn tối thiểu.";
                case NEAR_EXPIRY -> "Hiện chưa có lô hàng nào sắp hết hạn trong khoảng thời gian này.";
                case STOCK_BY_PRODUCT -> "Không, tôi chưa tìm thấy tồn kho phù hợp với sản phẩm hoặc mã hàng bạn hỏi.";
                case STOCK_LOWEST -> "Hiện chưa có dữ liệu tồn kho để xác định sản phẩm thấp nhất.";
                case STOCK_HIGHEST -> "Hiện chưa có dữ liệu tồn kho để xác định sản phẩm cao nhất.";
                case PRODUCT_BY_BARCODE -> "Mã vạch chưa được đăng ký hoặc không tồn tại.";
                case STOCK_BELOW_THRESHOLD -> "Hiện chưa có sản phẩm nào dưới ngưỡng tồn kho bạn hỏi.";
                case WAREHOUSE_STOCK_SUMMARY -> "Hiện chưa có dữ liệu tồn kho phù hợp với câu hỏi này.";
                case SLOW_MOVING_STOCK -> "Hiện chưa có dữ liệu giao dịch tồn kho để xác định sản phẩm quay vòng chậm.";
                case LOCATION_SEARCH -> "Tôi chưa tìm thấy vị trí kho phù hợp với điều kiện này.";
                case BEST_HEAVY_LOCATION -> "Hiện chưa có vị trí phù hợp cho hàng nặng theo dữ liệu hiện tại.";
                case SUPPLIER_TOP -> "Hiện chưa có dữ liệu đơn nhập phù hợp để xếp hạng nhà cung cấp.";
                case PENDING_PUTAWAY -> "Hiện chưa có việc xếp hàng lên kệ nào đang chờ xử lý.";
                case PUTAWAY_BY_WAREHOUSE -> "Hiện chưa có việc xếp hàng lên kệ đang chờ để tổng hợp theo kho.";
                case INBOUND_TODAY -> "Hôm nay chưa có lô hàng/phiếu nhập kho nào được ghi nhận.";
                case LATEST_INBOUND -> "Hiện chưa có phiếu nhập kho nào trong dữ liệu.";
                case INBOUND_RECEIPT_STATUS, INBOUND_RECEIPT_DETAIL -> "Tôi chưa tìm thấy phiếu nhận hàng phù hợp.";
                case PENDING_PO_RECEIPT -> "Hiện chưa có PO nào đang chờ nhận hàng.";
                case PURCHASE_ORDER_STATUS -> "Tôi chưa tìm thấy đơn nhập phù hợp.";
                case PURCHASE_ORDER_APPROVAL_AUDIT -> "Tôi chưa tìm thấy bản ghi phê duyệt phiếu nhập phù hợp.";
                case OUTBOUND_PRIORITY -> "Hiện chưa có đơn xuất nào trong nhóm cần ưu tiên theo dữ liệu hiện tại.";
                case PACKING_STATUS -> "Hiện chưa có đơn xuất nào đang chờ đóng gói.";
                case PICKING_TOP -> "Hiện chưa có dữ liệu lấy hàng để xếp hạng sản phẩm.";
                case PICKING_STATUS -> "Hiện chưa có dòng lấy hàng nào đang mở.";
                case ACTIVE_CYCLE_COUNTS -> "Hiện không có lịch kiểm kê định kỳ nào đang diễn ra.";
                case CYCLE_COUNT_VARIANCE -> "Hiện chưa ghi nhận dòng kiểm kê đang lệch tồn hoặc đang chờ đếm.";
                case CYCLE_COUNT_STATUS -> "Tôi chưa tìm thấy phiên kiểm kê phù hợp.";
                case RMA_PENDING -> "Hiện không có yêu cầu trả hàng RMA nào đang chờ xử lý.";
                case NOTIFICATION_LIST -> "Hiện bạn chưa có thông báo phù hợp.";
                case CATEGORY_LIST -> "Hiện chưa có danh mục sản phẩm phù hợp.";
                case PRODUCT_BY_CATEGORY -> "Tôi chưa tìm thấy sản phẩm nào thuộc danh mục này.";
                case STOCK_BY_LOCATION -> "Vị trí này hiện chưa có tồn kho phù hợp.";
                case STOCK_BY_LOT -> "Tôi chưa tìm thấy tồn kho cho lô bạn hỏi.";
                case DEAD_STOCK -> "Hiện chưa có mã hàng nào thuộc nhóm hàng chết theo điều kiện này.";
                case STOCK_AT_RISK -> "Hiện chưa có lô tồn kho rủi ro theo điều kiện này.";
                case REORDER_SUGGESTION -> "Hiện chưa có sản phẩm nào cần gợi ý đặt/nhập bổ sung.";
                case INVENTORY_VALUE_BY_WAREHOUSE -> "Hiện chưa có dữ liệu giá trị tồn kho theo kho phù hợp.";
                case LONGEST_EXPIRY_STOCK -> "Hiện chưa có lô hàng còn tồn với hạn sử dụng để xếp hạng.";
                case INBOUND_PRODUCT_QTY -> "Tôi chưa tìm thấy dữ liệu nhập kho phù hợp với sản phẩm/kỳ bạn hỏi.";
                case INBOUND_PENDING_PUTAWAY -> "Hiện chưa có phiếu nhập hoặc việc nào đang chờ xếp hàng lên kệ.";
                case OUTBOUND_DELAYED -> "Hiện chưa có đơn xuất nào đang trễ hoặc có nguy cơ trễ theo dữ liệu hiện tại.";
                case STOCK_FASTEST_DECREASE -> "Hiện chưa có biến động giảm tồn kho trong 7 ngày qua.";
                case PRODUCT_WITHOUT_LOCATION -> "Hiện chưa ghi nhận sản phẩm nào chưa được gán vị trí.";
                case PICK_LOCATION_USAGE -> "Hiện chưa có dữ liệu vị trí lấy hàng phù hợp.";
                case OUTBOUND_DELAY_REASON -> "Hiện chưa ghi nhận lý do xuất chậm trong lịch sử thao tác.";
                case CYCLE_COUNT_RECOUNT_SKUS -> "Hiện chưa có mã hàng cần kiểm kê lại theo dữ liệu sai lệch.";
                case RMA_QC_REQUIRED -> "Hiện chưa có RMA đang chờ kiểm tra chất lượng.";
                case RMA_DETAIL -> "Tôi chưa tìm thấy RMA phù hợp.";
                case RMA_LATEST_REASON -> "Hiện chưa có RMA nào trong dữ liệu.";
                case RMA_BY_SKU -> "Tôi chưa tìm thấy yêu cầu trả hàng liên quan mã hàng/sản phẩm bạn hỏi.";
                case RMA_SUPPLIER_RETURN -> "Hiện chưa có RMA nào cần gửi về nhà cung cấp.";
                case TOP_CUSTOMERS -> "Hiện chưa có dữ liệu khách hàng phù hợp để xếp hạng.";
                case SUPPLIER_PERFORMANCE -> "Hiện chưa có dữ liệu hiệu suất nhà cung cấp phù hợp.";
                case WAREHOUSE_CAPACITY -> "Hiện chưa có dữ liệu công suất kho phù hợp.";
                case PICKING_PRODUCTIVITY -> "Hiện chưa có dữ liệu năng suất lấy hàng.";
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
                case PRODUCT_LIST -> productListReply(map, route);
                case SUPPLIER_LIST, SUPPLIER_SEARCH -> supplierListReply(map, route);
                case CUSTOMER_LIST, CUSTOMER_SEARCH -> customerListReply(map, route);
                case GLOBAL_SEARCH -> globalSearchReply(map);
                case STOCK_TOTAL -> "Tổng tồn kho hiện có: " + value(map, "qty_on_hand") + " đơn vị, đã giữ chỗ "
                        + value(map, "qty_reserved") + ", khả dụng " + value(map, "qty_available")
                        + " trên " + value(map, "stocked_skus") + " mã hàng có tồn.";
                case LOT_TRACKED_COUNT -> "Có " + value(map, "lot_tracked") + " / " + value(map, "total")
                        + " sản phẩm đang hoạt động được theo dõi theo lô. Sản phẩm theo dõi hạn dùng: "
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
                case LOW_STOCK -> lowStockReply(list, route);
                case SLOW_MOVING_STOCK -> slowMovingStockReply(list);
                case WAREHOUSE_STOCK_SUMMARY -> warehouseStockSummaryReply(list);
                case BEST_HEAVY_LOCATION -> heavyLocationReply(list);
                case NEAR_EXPIRY -> nearExpiryReply(list, route);
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
                case LOCATION_SEARCH -> locationReply(list, route);
                case AUDIT_LOG -> auditLogReply(list);
                case AI_AUDIT_LOG -> aiAuditLogReply(list);
                case NOTIFICATION_LIST -> notificationReply(list);
                case CATEGORY_LIST -> categoryReply(list, route);
                case PRODUCT_BY_CATEGORY -> productByCategoryReply(list, route);
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
                case PRODUCT_WITHOUT_LOCATION -> productsWithoutLocationReply(list);
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
                case USER_LOOKUP -> userLookupReply(list, route);
                case ROLE_LIST -> roleListReply(list, route);
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

    private String productListReply(Map<?, ?> map, AiIntentResult route) {
        Object itemsObject = map.get("items");
        if (!(itemsObject instanceof List<?> items)) {
            return "Tôi chưa lấy được danh sách sản phẩm phù hợp.";
        }
        if (items.isEmpty()) {
            return "Hiện chưa có sản phẩm nào phù hợp với điều kiện bạn hỏi.";
        }
        List<?> displayed = displayRows(items, route, 5);
        long total = longValue(value(map, "total"));
        return "Có " + value(map, "total") + " sản phẩm trong hệ thống. "
                + (displayed.size() < total ? "Một số sản phẩm gần nhất:\n" : "Danh sách sản phẩm:\n")
                + joinRowsAsBulletList(displayed, row -> value(row, "sku") + " - " + value(row, "product_name")
                + " (" + value(row, "category_name") + ", " + statusValue(row, "status") + ")")
                + continuationSuffix(total, displayed.size(), items.size(), "sản phẩm",
                        "hiển thị tất cả sản phẩm", "lọc theo danh mục, trạng thái hoặc tên sản phẩm");
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

    private String supplierListReply(Map<?, ?> map, AiIntentResult route) {
        Object itemsObject = map.get("items");
        if (!(itemsObject instanceof List<?> items) || items.isEmpty()) {
            return "Tôi chưa tìm thấy nhà cung cấp phù hợp.";
        }
        List<?> displayed = displayRows(items, route, 6);
        long total = longValue(value(map, "total"));
        return "Tìm thấy " + value(map, "total") + " nhà cung cấp. "
                + (displayed.size() < total ? "Một số dòng:\n" : "Danh sách nhà cung cấp:\n")
                + joinRowsAsBulletList(displayed, row -> value(row, "code") + " - " + value(row, "name")
                + " (" + statusValue(row, "status") + ", liên hệ " + value(row, "contact_name") + ")")
                + continuationSuffix(total, displayed.size(), items.size(), "nhà cung cấp",
                        "hiển thị tất cả nhà cung cấp", "lọc theo trạng thái, tên hoặc mã nhà cung cấp");
    }

    private String customerListReply(Map<?, ?> map, AiIntentResult route) {
        Object itemsObject = map.get("items");
        if (!(itemsObject instanceof List<?> items) || items.isEmpty()) {
            return "Tôi chưa tìm thấy khách hàng phù hợp.";
        }
        List<?> displayed = displayRows(items, route, 6);
        long total = longValue(value(map, "total"));
        return "Tìm thấy " + value(map, "total") + " khách hàng. "
                + (displayed.size() < total ? "Một số dòng:\n" : "Danh sách khách hàng:\n")
                + joinRowsAsBulletList(displayed, row -> value(row, "code") + " - " + value(row, "name")
                + " (" + activeText(value(row, "is_active")) + ", liên hệ " + value(row, "contact_name") + ")")
                + continuationSuffix(total, displayed.size(), items.size(), "khách hàng",
                        "hiển thị tất cả khách hàng", "lọc theo trạng thái, tên hoặc mã khách hàng");
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
        String requestedWarehouseCandidate = firstNonBlank(list, "requested_warehouse_code");
        if ("N/A".equals(requestedWarehouseCandidate)) {
            requestedWarehouseCandidate = routeParam(route, "warehouseCode", "warehouse");
        }
        final String requestedWarehouse = requestedWarehouseCandidate;
        boolean availabilityQuestion = containsAny(query, "co trong kho", "co o kho", "co tai kho", "trong kho khong",
                "con hang", "con khong");

        if (containsAny(query, "o kho nao", "tai kho nao", "kho nao co", "nam o kho nao")
                && !(query.contains("nhieu") && query.contains("nhat"))) {
            String warehouses = byWarehouse.keySet().stream()
                    .filter(code -> !"N/A".equals(code))
                    .filter(code -> byWarehouse.get(code)[0] > 0)
                    .map(code -> "**" + code + "**")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(null);
            if (warehouses == null) {
                if (totalOnHand <= 0) {
                    return "Không, " + productLabel + " hiện chưa có tồn kho ở bất kỳ kho nào theo dữ liệu hiện tại.";
                }
                return productLabel + " có tồn tổng **" + formatNumber(totalOnHand)
                        + "** đơn vị, nhưng dữ liệu tồn hiện chưa gắn được kho cụ thể.";
            }
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

        if (availabilityQuestion) {
            if (requestedWarehouse != null) {
                long requestedOnHand = warehouseQty(byWarehouse, requestedWarehouse, 0);
                long requestedAvailable = warehouseQty(byWarehouse, requestedWarehouse, 2);
                if (requestedOnHand > 0) {
                    return "Có, " + productLabel + " hiện có ở kho **" + requestedWarehouse + "**.\n"
                            + "- **Tồn hiện có:** **" + formatNumber(requestedOnHand) + "** đơn vị\n"
                            + "- **Khả dụng:** **" + formatNumber(requestedAvailable) + "** đơn vị.\n"
                            + stockInsight(requestedOnHand, requestedAvailable, 0);
                }
                String otherWarehouses = byWarehouse.entrySet().stream()
                        .filter(entry -> !"N/A".equals(entry.getKey()))
                        .filter(entry -> !entry.getKey().equalsIgnoreCase(requestedWarehouse))
                        .filter(entry -> entry.getValue()[0] > 0)
                        .map(entry -> "**" + entry.getKey() + "** (" + formatNumber(entry.getValue()[0]) + " đơn vị)")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(null);
                if (otherWarehouses != null) {
                    return "Không, " + productLabel + " hiện chưa có tồn ở kho **" + requestedWarehouse
                            + "**, nhưng còn ở kho khác: " + otherWarehouses + ".";
                }
                return "Không, " + productLabel + " hiện chưa có tồn kho ở kho **" + requestedWarehouse + "**.";
            }
            if (totalOnHand <= 0) {
                return "Không, " + productLabel + " hiện chưa có tồn kho ở bất kỳ kho nào theo dữ liệu hiện tại.";
            }
            String warehouses = byWarehouse.keySet().stream()
                    .filter(code -> !"N/A".equals(code))
                    .filter(code -> byWarehouse.get(code)[0] > 0)
                    .map(code -> "**" + code + "**")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("chưa xác định kho");
            return "Có, " + productLabel + " hiện có trong kho.\n"
                    + "- **Tổng tồn hiện có:** **" + formatNumber(totalOnHand) + "** đơn vị\n"
                    + "- **Khả dụng:** **" + formatNumber(totalAvailable) + "** đơn vị\n"
                    + "- **Kho có hàng:** " + warehouses + ".\n"
                    + stockInsight(totalOnHand, totalAvailable, totalReserved);
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
                   "- **Trạng thái khả dụng:** " + availableText + ".\n" +
                   stockInsight(totalOnHand, totalAvailable, totalReserved);
        }

        if (totalOnHand <= 0 && totalAvailable <= 0) {
            return "**Thông tin tồn kho " + productLabel + " trên toàn hệ thống:**\n" +
                   "- **Tồn kho hiện có:** **0** đơn vị\n" +
                   "- **Khả dụng:** **0** đơn vị\n" +
                   "- **Trạng thái:** Chưa có tồn ở bất kỳ kho nào theo dữ liệu hiện tại.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Thông tin tồn kho ").append(productLabel).append(" trên toàn hệ thống:**\n")
          .append("- **Tổng tồn hiện có:** **").append(formatNumber(totalOnHand)).append("** đơn vị\n")
          .append("- **Khả dụng:** **").append(formatNumber(totalAvailable)).append("** đơn vị\n")
          .append("- **Đang giữ chỗ:** **").append(formatNumber(totalReserved)).append("** đơn vị\n\n")
          .append(stockInsight(totalOnHand, totalAvailable, totalReserved)).append("\n\n")
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
            value(row, "product_name") + " (mã hàng: **" + value(row, "sku") + "**) - Khả dụng: **" + formatNumber(longValue(row, "qty_available")) + "** " +
            "(Tồn hiện có: " + formatNumber(longValue(row, "qty_on_hand")) + ", Định mức tối thiểu: " + formatNumber(longValue(row, "min_stock_qty")) + ")"
        );
        return prefix + items;
    }

    private String stockHighestReply(List<?> list) {
        Object first = list.get(0);
        String prefix = "**Sản phẩm có tồn kho cao nhất:** **" + value(first, "product_name") + "**\n" +
                       "- **Mã hàng:** **" + value(first, "sku") + "**\n" +
                       "- **Số lượng tồn hiện có:** **" + formatNumber(longValue(first, "qty_on_hand")) + "** đơn vị\n" +
                       "- **Khả dụng:** **" + formatNumber(longValue(first, "qty_available")) + "** đơn vị\n\n" +
                       "**Danh sách Top 5 sản phẩm tồn kho cao nhất:**\n";
        String items = joinRowsAsBulletList(limit(list, 5), row -> 
            value(row, "product_name") + " (mã hàng: **" + value(row, "sku") + "**): **" + formatNumber(longValue(row, "qty_on_hand")) + "** đơn vị"
        );
        return prefix + items;
    }

    private String productByBarcodeReply(List<?> list, AiIntentResult route) {
        if (containsAny(routeQuery(route), "khong ra", "khong nhan", "scan loi", "scan khong")) {
            return "Mã vạch chưa được đăng ký hoặc không tồn tại.";
        }
        return joinRows(list, row -> "Mã vạch " + value(row, "barcode_ean13") + " thuộc mã hàng "
                + value(row, "sku") + " - " + value(row, "product_name") + ".");
    }

    private String stockBelowThresholdReply(List<?> list) {
        List<?> displayed = limit(list, 8);
        String suffix = list.size() > displayed.size()
                ? "\n\n*Còn " + formatNumber(list.size() - displayed.size()) + " sản phẩm khác chưa hiển thị.*"
                : "";
        return "**Có " + formatNumber(list.size()) + " sản phẩm dưới ngưỡng tồn kho yêu cầu:**\n"
                + joinRowsAsBulletList(displayed, row -> value(row, "product_name")
                + metricSuffix(row, "qty_on_hand", "tồn"))
                + suffix;
    }

    private String lowStockReply(List<?> list, AiIntentResult route) {
        boolean showAll = containsAny(routeQuery(route),
                "hien thi tat ca", "xem tat ca", "xem du", "show all", "all low stock");
        List<?> displayed = showAll ? list : limit(list, 5);
        String suffix = list.size() > displayed.size()
                ? "\n\n*Còn " + formatNumber(list.size() - displayed.size())
                + " sản phẩm khác chưa hiển thị. Bạn có thể nhắn: \"hiển thị tất cả sản phẩm tồn thấp\" để xem đủ "
                + formatNumber(list.size()) + " sản phẩm đã tìm thấy.*"
                : "";
        return "**Có " + formatNumber(list.size()) + " sản phẩm dưới mức tồn tối thiểu:**\n"
                + joinRowsAsBulletList(displayed, row -> "**Mã hàng " + value(row, "sku") + "**"
                + readableSuffix(row, "product_name")
                + metricSuffix(row, "qty_available", "khả dụng")
                + metricSuffix(row, "min_stock_qty", "tối thiểu"))
                + suffix;
    }

    private String slowMovingStockReply(List<?> list) {
        Object first = list.get(0);
        String days = value(first, "days_without_movement");
        if ("N/A".equals(days)) {
            return "Mã hàng " + value(first, "sku") + " chưa từng phát sinh giao dịch tồn kho theo dữ liệu hiện tại.";
        }
        return "Mã hàng " + value(first, "sku") + " không phát sinh giao dịch " + formatNumber(days) + " ngày.";
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
                + value(first, "location_code") + ", khu " + value(first, "zone")
                + ", sức chứa tối đa " + value(first, "max_weight_kg") + " kg.";
    }

    private String nearExpiryReply(List<?> list, AiIntentResult route) {
        boolean showAll = containsAny(routeQuery(route), "tat ca", "toan bo", "day du", "all");
        List<?> displayed = showAll ? list : limit(list, 10);
        String prefix = "**Phát hiện " + list.size() + " lô hàng sắp hết hạn hoặc đã quá hạn:**\n\n";
        String items = joinRowsAsBulletList(displayed, row -> {
            long daysLeft = longValue(value(row, "days_left"));
            String statusText = daysLeft < 0 
                ? "**Đã quá hạn " + Math.abs(daysLeft) + " ngày**" 
                : "còn **" + daysLeft + " ngày**";
            return value(row, "product_name") + " (Kho **" + value(row, "warehouse_code") + "**) " +
                   "- Hạn dùng: `" + value(row, "expiry_date") + "` (" + statusText + ")";
        });
        String suffix = "";
        if (!showAll && list.size() > displayed.size()) {
            suffix = "\n\n*(Đang hiển thị " + displayed.size() + " lô khẩn cấp nhất. "
                    + "Bạn có thể nhắn: \"hiển thị tất cả lô sắp hết hạn\" để xem đủ "
                    + list.size() + " lô đã tìm thấy.)*";
        }
        if (list.size() >= 50) {
            suffix += "\n\n*Lưu ý: kết quả truy vấn hiện giới hạn 50 lô đầu tiên. "
                    + "Nếu cần xem sâu hơn, hãy lọc thêm theo kho hoặc khoảng ngày, ví dụ: "
                    + "\"lô sắp hết hạn ở kho WH-HN trong 7 ngày tới\".*";
        }
        return prefix + items + suffix;
    }

    private String putawayReply(List<?> list, AiIntentResult route) {
        String query = routeQuery(route);
        Object first = list.get(0);
        String taskCode = routeParam(route, "putawayTaskCode", "taskCode", "code");
        if (taskCode != null) {
            if (containsAny(query, "dua hang di dau", "di dau", "de hang", "cat vao dau", "vao bin nao")) {
                return "Chuyển mã hàng " + value(first, "sku") + " vào vị trí " + value(first, "suggested_location") + ".";
            }
            if (containsAny(query, "xong chua", "hoan tat chua", "done", "completed")) {
                return taskCode + " đang ở trạng thái " + statusLabel(value(first, "status")) + ".";
            }
            return taskCode + " trạng thái " + statusLabel(value(first, "status"))
                    + ", vị trí gợi ý " + value(first, "suggested_location") + ".";
        }
        List<?> displayed = limit(list, 6);
        String suffix = list.size() > displayed.size()
                ? "\n\n*Còn " + formatNumber(list.size() - displayed.size()) + " việc khác chưa hiển thị.*"
                : "";
        long totalQty = sumLong(list, "qty_to_putaway");
        return "**Có " + formatNumber(list.size()) + " việc xếp hàng lên kệ đang chờ/xử lý:**\n"
                + "- **Tổng số lượng cần xếp kệ:** **" + formatNumber(totalQty) + "** đơn vị\n\n"
                + joinRowsAsBulletList(displayed, row -> productDisplay(row)
                + "\n  - Số lượng: **" + formatNumber(longValue(row, "qty_to_putaway")) + "**"
                + "\n  - Trạng thái: `" + statusValue(row, "status") + "`"
                + "\n  - Vị trí gợi ý: `" + value(row, "suggested_location") + "`")
                + suffix
                + "\n\n**Khuyến nghị:** xử lý trước các việc có số lượng lớn hoặc vị trí gợi ý rõ ràng để giảm hàng chờ xếp kệ.";
    }

    private String putawayByWarehouseReply(List<?> list) {
        Object first = list.get(0);
        return "Kho có nhiều việc xếp hàng lên kệ nhất là " + value(first, "warehouse_code")
                + " với " + value(first, "task_count") + " việc, tổng số lượng "
                + value(first, "qty_to_putaway") + ". Chi tiết: "
                + joinRows(list, row -> value(row, "warehouse_code") + " có "
                + value(row, "task_count") + " việc");
    }

    private String inboundTodayReply(List<?> list) {
        List<?> displayed = limit(list, 8);
        String suffix = list.size() > displayed.size()
                ? "\n\n*Còn " + formatNumber(list.size() - displayed.size()) + " dòng khác chưa hiển thị.*"
                : "";
        return "**Hôm nay có " + formatNumber(list.size()) + " dòng hàng nhập:**\n"
                + joinRowsAsBulletList(displayed, row -> "`" + value(row, "receipt_number") + "` - "
                + value(row, "product_name")
                + "\n  - Số lượng: **" + formatNumber(longValue(row, "received_qty")) + "**"
                + "\n  - NCC: " + value(row, "supplier_name"))
                + suffix;
    }

    private String latestInboundReply(List<?> list) {
        Object first = list.get(0);
        return "**Phiếu nhập gần nhất:** `" + value(first, "receipt_number") + "`\n"
                + "- Ngày nhận: `" + value(first, "received_date") + "`\n"
                + "- Thuộc PO: `" + value(first, "po_number") + "`\n"
                + "- Nhà cung cấp: " + value(first, "supplier_name") + "\n"
                + "- Người xử lý/nhận hàng: " + value(first, "received_by") + "\n\n"
                + "**Dòng hàng gần nhất:**\n"
                + joinRowsAsBulletList(limit(list, 5), row -> value(row, "product_name")
                + " - số lượng **" + formatNumber(longValue(row, "received_qty")) + "**");
    }

    private String inboundReceiptStatusReply(List<?> list) {
        Object first = list.get(0);
        String receipt = value(first, "receipt_number");
        String status = value(first, "receipt_status");
        boolean waitingPutaway = list.stream()
                .map(row -> value(row, "putaway_status"))
                .anyMatch(statusValue -> List.of("PENDING", "IN_PROGRESS", "ASSIGNED").contains(statusValue));
        if ("RECEIVED".equalsIgnoreCase(status) && waitingPutaway) {
            return "Phiếu nhận hàng " + receipt + " đã hoàn tất nhận hàng, đang chờ xếp hàng lên kệ.";
        }
        return "Phiếu nhận hàng " + receipt + " đang ở trạng thái " + statusLabel(status) + ".";
    }

    private String inboundReceiptDetailReply(List<?> list, AiIntentResult route) {
        String query = routeQuery(route);
        Object first = list.get(0);
        String receipt = value(first, "receipt_number");
        if (containsAny(query, "ai dang", "ai xu ly", "nguoi xu ly", "assigned", "gan cho ai")) {
            String assignee = firstNonBlank(list, "assignee_username");
            return "Việc này được gán cho " + ("N/A".equals(assignee) ? "N/A" : assignee) + ".";
        }
        String location = firstNonBlank(list, "suggested_location");
        String warehouse = firstNonBlank(list, "suggested_warehouse_code");
        return "Hệ thống đề xuất xếp hàng vào vị trí " + location + ", kho " + warehouse + ".";
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
        List<?> displayed = limit(list, 6);
        String suffix = list.size() > displayed.size()
                ? "\n\n*Còn " + formatNumber(list.size() - displayed.size()) + " PO khác chưa hiển thị.*"
                : "";
        long totalRemaining = sumLong(list, "remaining_qty");
        return "**Có " + formatNumber(list.size()) + " PO đang chờ nhận/chưa hoàn tất:**\n"
                + "- **Tổng số lượng còn phải nhận:** **" + formatNumber(totalRemaining) + "** đơn vị\n\n"
                + joinRowsAsBulletList(displayed, row -> "**" + value(row, "po_number") + "**"
                + " - " + value(row, "supplier_name")
                + "\n  - Trạng thái: `" + statusValue(row, "status") + "`"
                + "\n  - Còn phải nhận: **" + formatNumber(longValue(row, "remaining_qty")) + "** đơn vị")
                + suffix
                + "\n\n**Khuyến nghị:** ưu tiên xác nhận lịch giao của các PO có số lượng còn phải nhận lớn, rồi đối chiếu với phiếu nhập/GR khi hàng về.";
    }

    private String outboundPriorityReply(List<?> list) {
        Object first = list.get(0);
        return "Đơn xuất ưu tiên cao nhất hiện tại là " + value(first, "so_number")
                + " của " + value(first, "customer_name")
                + ", mức ưu tiên " + value(first, "priority")
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
                    return "Chưa, đơn vẫn đang ở bước lấy hàng.";
                }
                return "Chưa, đơn hiện ở trạng thái " + statusLabel(status) + ".";
            }
            if (containsAny(query, "trang thai", "tinh trang", "status", "dang o trang thai")) {
                return "Đơn xuất " + value(first, "so_number") + " đang ở trạng thái "
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
                        return formatNumber(remaining) + " đơn vị mã hàng " + value(row, "sku");
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
        return "Có " + list.size() + " đơn xuất đang ở bước trước/trong đóng gói: " + joinRows(limit(list, 6), row ->
                value(row, "so_number") + " trạng thái " + statusValue(row, "status")
                        + ", khách " + value(row, "customer_name"));
    }

    private String pickingTopReply(List<?> list) {
        Object first = list.get(0);
        return "Sản phẩm cần lấy nhiều nhất là " + value(first, "product_name")
                + " với số lượng cần lấy " + value(first, "qty_to_pick")
                + ". Top: " + joinRows(limit(list, 5), row -> value(row, "product_name")
                + " cần lấy " + value(row, "qty_to_pick"));
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
        if (containsAny(query, "don lay hang", "don nao", "don chua", "chua hoan thanh", "chua hoan tat")) {
            return pickingOrdersReply(list);
        }
        if (containsAny(query, "con bao nhieu mon", "bao nhieu mon chua", "chua lay", "chua hoan tat")) {
            long remainingSku = list.stream()
                    .filter(row -> longValue(row, "remaining_qty") > 0 || !"PICKED".equalsIgnoreCase(value(row, "status")))
                    .map(row -> value(row, "sku"))
                    .distinct()
                    .count();
            return "Còn " + formatNumber(remainingSku) + " mã hàng chưa hoàn tất lấy hàng.";
        }
        if (containsAny(query, "uu tien", "priority")) {
            long priority = longValue(first, "priority");
            if (priority > 0 && priority <= 2) {
                return "Đây là việc ưu tiên cao do đơn giao gấp.";
            }
            return "Việc này không nằm trong nhóm ưu tiên cao theo dữ liệu hiện tại.";
        }
        if (containsAny(query, "ai dang", "ai pick", "ai lay hang", "nguoi pick", "assigned")) {
            return "Việc này đang được xử lý bởi " + value(first, "assignee_username") + ".";
        }
        if (containsAny(query, "lay hang o dau", "lay tu dau", "lay hang tai dau", "bin nao", "vi tri nao")) {
            return "Lấy " + formatNumber(value(first, "qty_to_pick")) + " đơn vị mã hàng " + value(first, "sku")
                    + " từ " + value(first, "location_code") + ", " + value(first, "warehouse_code") + ".";
        }
        return "Có " + list.size() + " việc lấy hàng/dòng lấy hàng phù hợp: " + joinRows(limit(list, 6), row ->
                value(row, "so_number") + " - " + value(row, "product_name")
                        + ", cần lấy " + value(row, "qty_to_pick")
                        + ", đã lấy " + value(row, "qty_picked")
                        + ", trạng thái " + statusValue(row, "status"));
    }

    private String pickingOrdersReply(List<?> list) {
        Map<String, long[]> byOrder = new LinkedHashMap<>();
        for (Object row : list) {
            String soNumber = value(row, "so_number");
            if ("N/A".equals(soNumber)) {
                continue;
            }
            long[] totals = byOrder.computeIfAbsent(soNumber, ignored -> new long[3]);
            totals[0]++;
            totals[1] += longValue(row, "remaining_qty");
            totals[2] += longValue(row, "qty_to_pick");
        }
        if (byOrder.isEmpty()) {
            return "Hiện chưa xác định được đơn lấy hàng nào chưa hoàn thành.";
        }
        List<Map<String, Object>> displayed = byOrder.entrySet().stream()
                .limit(6)
                .map(entry -> Map.<String, Object>of(
                        "so_number", entry.getKey(),
                        "line_count", entry.getValue()[0],
                        "remaining_qty", entry.getValue()[1],
                        "qty_to_pick", entry.getValue()[2]))
                .toList();
        String suffix = byOrder.size() > displayed.size()
                ? "\n\n*Còn " + formatNumber(byOrder.size() - displayed.size()) + " đơn khác chưa hiển thị.*"
                : "";
        return "**Có " + formatNumber(byOrder.size()) + " đơn lấy hàng chưa hoàn thành:**\n"
                + joinRowsAsBulletList(displayed, row -> "**" + value(row, "so_number") + "**"
                + " - " + formatNumber(longValue(row, "line_count")) + " dòng chưa xong"
                + ", còn **" + formatNumber(longValue(row, "remaining_qty")) + "** / "
                + formatNumber(longValue(row, "qty_to_pick")) + " sản phẩm cần lấy")
                + suffix;
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
            return "Không, mã hàng này chưa ghi nhận chênh lệch trong phiên kiểm kê.";
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
        return "Có " + list.size() + " dòng trả hàng liên quan mã hàng/sản phẩm bạn hỏi: "
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

    private String locationReply(List<?> list, AiIntentResult route) {
        List<?> displayed = displayRows(list, route, 8);
        return "Tìm thấy " + list.size() + " vị trí:\n" + joinRowsAsBulletList(displayed, row ->
                value(row, "warehouse_code") + "/" + value(row, "code")
                        + " khu " + value(row, "zone")
                        + ", trạng thái " + statusValue(row, "status"))
                + continuationSuffix(list.size(), displayed.size(), list.size(), "vị trí",
                        "hiển thị tất cả vị trí", "lọc theo kho, khu hoặc trạng thái vị trí");
    }

    private String auditLogReply(List<?> list) {
        return "Có " + list.size() + " dòng lịch sử thao tác gần nhất: " + joinRows(limit(list, 8), row ->
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
               "- **Việc xếp hàng lên kệ đang chờ:** **" + formatNumber(longValue(nestedValue(map, "pending_putaway", "total"))) + "** việc\n" +
               "- **Đơn đang chờ lấy hàng:** **" + formatNumber(longValue(nestedValue(map, "pending_picking", "total"))) + "** đơn hàng\n" +
               "- **Đợt kiểm kê đang mở:** **" + formatNumber(longValue(nestedValue(map, "active_cycle_counts", "total"))) + "** đợt kiểm kê\n" +
               "- **Lô hàng sắp hết hạn (trong 7 ngày):** **" + formatNumber(longValue(nestedValue(map, "near_expiry_7_days", "total"))) + "** lô\n" +
               "- **Yêu cầu trả hàng (RMA) chờ xử lý:** **" + formatNumber(longValue(nestedValue(map, "pending_rma", "total"))) + "** yêu cầu.";
    }

    private String reportSummaryReply(Map<?, ?> map) {
        return "📊 **Tổng quan tình hình vận hành hệ thống:**\n" +
               "- **Kho hoạt động:** **" + formatNumber(longValue(nestedValue(map, "warehouses", "active"))) + "** kho đang hoạt động\n" +
               "- **Tổng tồn kho hiện có:** **" + formatNumber(longValue(nestedValue(map, "stock", "qty_on_hand"))) + "** đơn vị\n" +
               "- **Tồn kho đang giữ chỗ:** **" + formatNumber(longValue(nestedValue(map, "stock", "qty_reserved"))) + "** đơn vị\n" +
               "- **Việc xếp hàng lên kệ đang chờ:** **" + formatNumber(longValue(nestedValue(map, "pending_putaway", "total"))) + "** việc\n" +
               "- **Đơn xuất ưu tiên cần xử lý:** **" + formatNumber(longValue(nestedValue(map, "priority_outbound", "total"))) + "** đơn.";
    }

    private String flowReportReply(Map<?, ?> map) {
        return "📈 **Báo cáo biến động nhập - xuất trong 7 ngày qua:**\n" +
               "📥 **Hoạt động nhập kho:**\n" +
               "  - Số phiếu nhập: **" + formatNumber(longValue(nestedValue(map, "inbound", "receipts"))) + "** phiếu\n" +
               "  - Tổng số lượng nhập: **" + formatNumber(longValue(nestedValue(map, "inbound", "received_qty"))) + "** đơn vị\n" +
               "📤 **Hoạt động xuất kho:**\n" +
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
                + "; biến động tồn kho " + nestedValue(map, "movements", "movements") + " lượt.";
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
                value(row, "product_name") + " (mã hàng: **" + value(row, "sku") + "**) ~ **" + formatCurrency(value(row, "inventory_value")) + "**"
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
        sb.append("**Phát hiện ").append(count).append(" sản phẩm đã ngừng kinh doanh nhưng vẫn còn tồn kho:**\n")
          .append("- **Tổng số lượng tồn:** **").append(totalQty).append("** đơn vị\n\n")
          .append("📋 **Một số sản phẩm tiêu biểu:**\n");
          
        Object items = map.get("items");
        if (items instanceof List<?> itemList && !itemList.isEmpty()) {
            sb.append(joinRowsAsBulletList(limit(itemList, 5), row -> 
                "**" + value(row, "sku") + "** - " + value(row, "product_name") + " " +
                "(Trạng thái: `" + statusValue(row, "status") + "`, Tồn: **" + formatNumber(longValue(row, "qty_on_hand")) + "** đơn vị)"
            ));
        } else {
            sb.append("- *Không có danh sách chi tiết*");
        }
        return sb.toString();
    }

    private String longestExpiryReply(List<?> list) {
        Object first = list.get(0);
        return "📅 **Lô hàng có hạn sử dụng dài nhất còn lại:**\n" +
               "- **Mã lô:** `" + value(first, "lot_number") + "`\n" +
               "- **Sản phẩm:** mã hàng **" + value(first, "sku") + "** - " + value(first, "product_name") + "\n" +
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
        String prefix = "📥 **Có " + list.size() + " dòng/phiếu nhập đang chờ xếp hàng lên kệ:**\n\n";
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

    private String productsWithoutLocationReply(List<?> list) {
        List<?> displayed = limit(list, 8);
        String suffix = list.size() > displayed.size()
                ? "\n\n*Còn " + formatNumber(list.size() - displayed.size()) + " sản phẩm khác chưa hiển thị.*"
                : "";
        return "**Có " + formatNumber(list.size()) + " sản phẩm cần kiểm tra vị trí:**\n"
                + joinRowsAsBulletList(displayed, row -> "`" + value(row, "sku") + "` - "
                + productNameDisplay(row)
                + "\n  - Lý do: `" + value(row, "reason") + "`"
                + "\n  - Tồn: **" + formatNumber(longValue(row, "qty_on_hand"))
                + "**, chờ xếp kệ: **" + formatNumber(longValue(row, "pending_putaway_qty")) + "**")
                + suffix;
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
        return "**Có " + formatNumber(longValue(nestedValue(map, "summary", "orders")))
                + " đơn xuất thiếu hàng để giao.**\n"
                + "- **Tổng thiếu/chưa pick:** **"
                + formatNumber(longValue(nestedValue(map, "summary", "shortage_qty"))) + "** đơn vị\n\n"
                + "**Một số dòng cần xử lý:**\n"
                + joinRowsFromMapListAsBulletList(map, "items", row -> "**" + value(row, "so_number")
                + "** - mã hàng `" + value(row, "sku") + "` thiếu **"
                + formatNumber(longValue(row, "shortage_qty")) + "** đơn vị", 5)
                + "\n\n**Khuyến nghị:** kiểm tra tồn khả dụng và đơn nhập đang chờ nhận cho các mã hàng thiếu nhiều nhất trước khi xác nhận lịch giao.";
    }

    private String cycleCountRecountSkusReply(List<?> list) {
        return "Các mã hàng cần kiểm kê lại sau sai lệch: " + joinRows(limit(list, 8), row ->
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
        return "Hoàn thành lấy hàng: " + value(map, "completed_orders")
                + " đơn, " + value(map, "completed_lines")
                + " dòng, tổng đã lấy " + value(map, "qty_picked") + " đơn vị.";
    }

    private String pickingCompletionRateReply(Map<?, ?> map) {
        return "Tỷ lệ hoàn thành lấy hàng là " + value(map, "line_completion_rate")
                + "% theo dòng việc và " + value(map, "qty_completion_rate")
                + "% theo số lượng đã lấy.";
    }

    private String pickingStockCheckReply(Map<?, ?> map) {
        return "Mã hàng " + value(map, "sku") + " hiện khả dụng " + value(map, "qty_available")
                + " đơn vị; nhu cầu lấy " + value(map, "requested_qty")
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
        return "Tỷ lệ hoàn thành lấy hàng là " + nestedValue(map, "picking", "line_completion_rate")
                + "% theo dòng việc; xếp hàng lên kệ là " + nestedValue(map, "putaway", "completion_rate")
                + "%, đã hoàn thành " + nestedValue(map, "putaway", "completed_tasks")
                + " / " + nestedValue(map, "putaway", "total_tasks") + " việc.";
    }

    private String employeeOperationProductivityReply(Map<?, ?> map) {
        return "Hiệu suất nhân viên: lấy hàng - " + joinRowsFromMapList(map, "picking", row ->
                value(row, "assignee") + " đã lấy " + value(row, "qty_picked") + "/"
                        + value(row, "qty_to_pick"))
                + "; xếp hàng lên kệ - " + joinRowsFromMapList(map, "putaway", row ->
                value(row, "assignee") + " hoàn thành " + value(row, "completed_lines")
                        + "/" + value(row, "task_lines") + " việc");
    }

    private String overdueTasksReply(Map<?, ?> map) {
        return "Việc quá hạn ước tính: lấy hàng " + nestedValue(map, "picking", "task_lines")
                + " dòng trên " + nestedValue(map, "picking", "orders")
                + " đơn, còn " + nestedValue(map, "picking", "remaining_qty")
                + " đơn vị; xếp hàng lên kệ " + nestedValue(map, "putaway", "task_lines")
                + " việc, số lượng " + nestedValue(map, "putaway", "qty_to_putaway") + ".";
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
        StringBuilder sb = new StringBuilder();
        sb.append("**Việc được giao hiện tại:**\n")
                .append("- Xếp hàng lên kệ: **").append(formatNumber(listSize(map, "putaway"))).append("** việc\n")
                .append("- Lấy hàng: **").append(formatNumber(listSize(map, "picking"))).append("** việc\n")
                .append("- Kiểm kê: **").append(formatNumber(listSize(map, "cycle_counts"))).append("** phiếu");
        appendMapListSection(sb, "Lấy hàng", map, "picking", row -> "**" + value(row, "so_number")
                + "** - mã hàng `" + value(row, "sku") + "`, cần **"
                + formatNumber(longValue(row, "qty_to_pick")) + "**, đã lấy **"
                + formatNumber(longValue(row, "qty_picked")) + "**", 5);
        appendMapListSection(sb, "Xếp hàng lên kệ", map, "putaway", row -> "`" + value(row, "sku") + "` - "
                + value(row, "product_name") + ", SL **"
                + formatNumber(longValue(row, "qty_to_putaway")) + "**, trạng thái `"
                + statusValue(row, "status") + "`", 5);
        return sb.toString();
    }

    private String notificationReply(List<?> list) {
        return "Bạn có " + list.size() + " thông báo gần nhất: " + joinRows(limit(list, 6), row ->
                value(row, "severity") + " - " + value(row, "title")
                        + " (" + value(row, "created_at") + ")");
    }

    private String categoryReply(List<?> list, AiIntentResult route) {
        List<?> displayed = displayRows(list, route, 8);
        String prefix = displayed.size() < list.size()
                ? "Có " + list.size() + " danh mục. Hiển thị " + displayed.size() + " danh mục đầu tiên:"
                : "Có " + list.size() + " danh mục:";
        return prefix + "\n" + joinRowsAsBulletList(displayed, row ->
                value(row, "code") + " - " + value(row, "name")
                        + ", sản phẩm " + value(row, "product_count"))
                + continuationSuffix(list.size(), displayed.size(), list.size(), "danh mục",
                        "hiển thị tất cả danh mục", "lọc theo tên hoặc mã danh mục");
    }

    private String productByCategoryReply(List<?> list, AiIntentResult route) {
        List<?> displayed = displayRows(list, route, 8);
        String prefix = displayed.size() < list.size()
                ? "Tìm thấy " + list.size() + " sản phẩm trong danh mục. Hiển thị "
                        + displayed.size() + " sản phẩm đầu tiên:"
                : "Tìm thấy " + list.size() + " sản phẩm trong danh mục:";
        return prefix + "\n" + joinRowsAsBulletList(displayed, row ->
                value(row, "sku") + " - " + value(row, "product_name")
                        + ", tồn " + value(row, "qty_on_hand"))
                + continuationSuffix(list.size(), displayed.size(), list.size(), "sản phẩm",
                        "hiển thị tất cả sản phẩm trong danh mục", "lọc thêm theo trạng thái hoặc tên sản phẩm");
    }

    private String stockByLocationReply(List<?> list) {
        return "Vị trí đang chứa: " + joinRows(limit(list, 8), row ->
                value(row, "warehouse_code") + "/" + value(row, "location_code")
                        + " - " + value(row, "sku") + " " + value(row, "product_name")
                        + ", lô " + value(row, "lot_number")
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
        return "Có " + list.size() + " mã hàng thuộc nhóm hàng chết/tồn lâu: " + joinRows(limit(list, 6), row ->
                value(row, "sku") + " - " + value(row, "product_name")
                        + ", tồn " + value(row, "qty_on_hand")
                        + ", giao dịch cuối " + value(row, "last_movement_at"));
    }

    private String stockAtRiskReply(List<?> list) {
        return "Có " + list.size() + " lô tồn kho rủi ro: " + joinRows(limit(list, 6), row ->
                value(row, "sku") + " lô " + value(row, "lot_number")
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
        List<?> displayed = limit(list, 8);
        String suffix = list.size() > displayed.size()
                ? "\n\n*Còn " + formatNumber(list.size() - displayed.size()) + " người khác chưa hiển thị.*"
                : "";
        return "**Năng suất lấy hàng theo người:**\n"
                + joinRowsAsBulletList(displayed, row -> "**" + assigneeDisplay(row) + "**"
                + "\n  - Đã lấy: **" + formatNumber(longValue(row, "qty_picked")) + "** / **"
                + formatNumber(longValue(row, "qty_to_pick")) + "** đơn vị"
                + "\n  - Dòng hoàn tất: **" + formatNumber(longValue(row, "completed_lines")) + "**")
                + suffix;
    }

    private String supplierPerformanceReply(List<?> list) {
        return "Hiệu suất NCC: " + joinRows(limit(list, 8), row ->
                value(row, "supplier_code") + " - " + value(row, "supplier_name")
                        + ": " + value(row, "purchase_orders") + " PO, hoàn tất "
                        + value(row, "completed_orders") + ", đúng hạn "
                        + value(row, "on_time_orders"));
    }

    private String userLookupReply(List<?> list, AiIntentResult route) {
        List<?> displayed = displayRows(list, route, 8);
        return "Tìm thấy " + list.size() + " người dùng:\n" + joinRowsAsBulletList(displayed, row ->
                value(row, "username") + " - " + value(row, "full_name")
                        + ", vai trò " + value(row, "roles")
                        + ", kho " + value(row, "warehouses"))
                + continuationSuffix(list.size(), displayed.size(), list.size(), "người dùng",
                        "hiển thị tất cả người dùng", "lọc theo tên, vai trò hoặc kho");
    }

    private String roleListReply(List<?> list, AiIntentResult route) {
        List<?> displayed = displayRows(list, route, 10);
        return "Hệ thống có " + list.size() + " vai trò:\n" + joinRowsAsBulletList(displayed, row ->
                value(row, "code") + " - " + value(row, "name")
                        + " (" + value(row, "user_count") + " user)")
                + continuationSuffix(list.size(), displayed.size(), list.size(), "vai trò",
                        "hiển thị tất cả vai trò", "lọc theo tên hoặc mã vai trò");
    }

    private String missingParameterReply(AiIntentResult route, AiToolResult toolResult) {
        AiIntent intent = route == null || route.getIntent() == null ? AiIntent.UNSUPPORTED : route.getIntent();
        String missing = toolResult.missingParams().stream()
                .map(this::paramLabel)
                .reduce((a, b) -> a + ", " + b)
                .orElse("thông tin cần thiết");
        return "**Mình chưa đủ thông tin để " + taskLabel(intent) + ".**\n"
                + "- **Cần bổ sung:** " + missing + "\n"
                + "- **Ví dụ:** \"" + exampleQuestion(intent) + "\"\n\n"
                + "Bạn gửi thêm thông tin đó, mình sẽ kiểm tra tiếp trên dữ liệu kho.";
    }

    private String clarificationReply(AiIntentResult route, AiToolResult toolResult) {
        String originalMessage = toolResult == null ? "" : toolResult.message();
        String message = originalMessage == null || originalMessage.isBlank()
                ? "Bạn vui lòng nói rõ thêm nghiệp vụ cần kiểm tra."
                : originalMessage;
        return "**Mình cần bạn làm rõ thêm câu hỏi.**\n"
                + "- " + message + "\n"
                + "- Bạn có thể hỏi theo mẫu: \"Mã hàng 00018 còn bao nhiêu?\", \"PO-2026-0001 đã nhận đủ chưa?\" hoặc \"Đơn xuất nào đang thiếu hàng?\"";
    }

    private String taskLabel(AiIntent intent) {
        return switch (intent) {
            case STOCK_BY_PRODUCT -> "kiểm tra tồn kho sản phẩm";
            case STOCK_BY_LOCATION -> "kiểm tra hàng theo vị trí";
            case STOCK_BY_LOT -> "kiểm tra tồn theo lô";
            case PURCHASE_ORDER_DETAIL, PURCHASE_ORDER_STATUS -> "kiểm tra phiếu nhập";
            case SALES_ORDER_DETAIL, SALES_ORDER_STATUS -> "kiểm tra đơn xuất";
            case WAREHOUSE_DETAIL -> "kiểm tra thông tin kho";
            default -> "xử lý yêu cầu này";
        };
    }

    private String exampleQuestion(AiIntent intent) {
        return switch (intent) {
            case STOCK_BY_PRODUCT -> "Mã hàng 00018 còn bao nhiêu trong kho WH-001?";
            case STOCK_BY_LOCATION -> "Vị trí A-01-02 đang chứa hàng gì?";
            case STOCK_BY_LOT -> "Lô LOT-2026-001 còn bao nhiêu?";
            case PURCHASE_ORDER_DETAIL, PURCHASE_ORDER_STATUS -> "PO-2026-0001 đã nhận đủ chưa?";
            case SALES_ORDER_DETAIL, SALES_ORDER_STATUS -> "SO-2026-0001 còn thiếu gì?";
            case WAREHOUSE_DETAIL -> "Kho WH-001 ở đâu?";
            default -> "Tình hình kho hôm nay có gì cần chú ý?";
        };
    }

    private String paramLabel(String param) {
        if (param == null || param.isBlank()) {
            return "thông tin cần thiết";
        }
        String normalized = normalize(param);
        if (normalized.contains("sku") || normalized.contains("product")) {
            return "mã hàng hoặc tên sản phẩm";
        }
        if (normalized.contains("warehouse")) {
            return "mã kho";
        }
        if (normalized.contains("location")) {
            return "mã vị trí";
        }
        if (normalized.contains("lot")) {
            return "mã lô";
        }
        if (normalized.contains("po")) {
            return "mã phiếu nhập/PO";
        }
        if (normalized.contains("so")) {
            return "mã đơn xuất/SO";
        }
        return param.replace('|', '/');
    }

    private String stockInsight(long onHand, long available, long reserved) {
        if (onHand <= 0) {
            return "- **Nhận định:** chưa có tồn để xuất. Nên kiểm tra phiếu nhập đang chờ nhận hoặc dữ liệu xếp hàng lên kệ.";
        }
        if (available <= 0) {
            return "- **Nhận định:** có tồn nhưng chưa khả dụng để xuất, khả năng đang bị giữ chỗ hoặc chưa hoàn tất xử lý kho.";
        }
        if (reserved > 0 && available < onHand) {
            return "- **Nhận định:** một phần tồn đang được giữ chỗ. Khi tạo đơn xuất mới, nên dùng số khả dụng thay vì tổng tồn.";
        }
        if (available <= 5) {
            return "- **Nhận định:** tồn khả dụng thấp, nên xem xét nhập bổ sung hoặc chuyển kho nếu sản phẩm bán nhanh.";
        }
        return "- **Nhận định:** tồn khả dụng đang ổn theo dữ liệu hiện tại.";
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
            return "Mã hàng " + sku + " - " + name;
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

    private long warehouseQty(Map<String, long[]> byWarehouse, String warehouseCode, int index) {
        if (warehouseCode == null) {
            return 0;
        }
        for (Map.Entry<String, long[]> entry : byWarehouse.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(warehouseCode)) {
                long[] values = entry.getValue();
                return index >= 0 && index < values.length ? values[index] : 0;
            }
        }
        return 0;
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
            case "ACTIVE" -> "đang bán/đang hoạt động";
            case "INACTIVE" -> "ngừng sử dụng";
            case "OUT_OF_STOCK" -> "tạm ngừng bán do hết hàng";
            case "NOT_FOUND" -> "không tìm thấy";
            case "PICKING" -> "đang lấy hàng";
            case "PENDING" -> "đang chờ xử lý";
            case "IN_PROGRESS" -> "đang xử lý";
            case "ASSIGNED" -> "đã được giao việc";
            case "PICKED" -> "đã lấy hàng";
            case "RECEIVED" -> "đã nhận hàng";
            case "PUTAWAY_IN_PROGRESS" -> "đang xếp hàng lên kệ";
            case "COMPLETED" -> "đã hoàn tất";
            case "APPROVED" -> "đã duyệt";
            case "CANCELLED" -> "đã hủy";
            case "SHIPPED" -> "đã giao hàng";
            case "PACKED" -> "đã đóng gói";
            case "ON_HOLD" -> "đang tạm giữ";
            case "OPEN" -> "đang mở";
            case "CLOSED" -> "đã đóng";
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

    private String joinRowsFromMapListAsBulletList(Map<?, ?> map, String key,
            java.util.function.Function<Object, String> formatter, int limit) {
        Object value = map.get(key);
        if (!(value instanceof List<?> rows) || rows.isEmpty()) {
            return "- Không có dữ liệu";
        }
        List<?> displayed = limit(rows, limit);
        String suffix = rows.size() > displayed.size()
                ? "\n\n*Còn " + formatNumber(rows.size() - displayed.size()) + " dòng khác chưa hiển thị.*"
                : "";
        return joinRowsAsBulletList(displayed, formatter) + suffix;
    }

    private void appendMapListSection(StringBuilder sb, String title, Map<?, ?> map, String key,
            java.util.function.Function<Object, String> formatter, int limit) {
        Object value = map.get(key);
        if (!(value instanceof List<?> rows) || rows.isEmpty()) {
            return;
        }
        sb.append("\n\n**").append(title).append(":**\n")
                .append(joinRowsFromMapListAsBulletList(map, key, formatter, limit));
    }

    private String readableSuffix(Object row, String key) {
        String value = value(row, key);
        return "N/A".equals(value) || value.isBlank() ? "" : " - " + value;
    }

    private String productDisplay(Object row) {
        String name = value(row, "product_name");
        if (!"N/A".equals(name) && !name.isBlank() && !looksGeneratedProductName(name)) {
            return name;
        }
        String sku = value(row, "sku");
        return !"N/A".equals(sku) && !sku.isBlank() ? "Mã hàng `" + sku + "`" : "Sản phẩm chưa có tên";
    }

    private String productNameDisplay(Object row) {
        String name = value(row, "product_name");
        if (!"N/A".equals(name) && !name.isBlank() && !looksGeneratedProductName(name)) {
            return name;
        }
        return "Sản phẩm chưa có tên";
    }

    private boolean looksGeneratedProductName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (isUuid(trimmed)) {
            return true;
        }
        String normalized = normalize(trimmed);
        if (!normalized.startsWith("san pham ")) {
            return false;
        }
        return isUuid(trimmed.substring(Math.min(trimmed.length(), "Sản phẩm ".length())).trim());
    }

    private String assigneeDisplay(Object row) {
        String assignee = value(row, "assignee");
        if ("N/A".equals(assignee) || assignee.isBlank()
                || "unassigned".equalsIgnoreCase(assignee)
                || isUuid(assignee)) {
            return "Chưa gán nhân viên";
        }
        return assignee;
    }

    private boolean isUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String metricSuffix(Object row, String key, String label) {
        String value = value(row, key);
        if ("N/A".equals(value) || value.isBlank()) {
            return "";
        }
        return ", " + label + " **" + formatNumber(longValue(value)) + "**";
    }

    private int listSize(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof List<?> rows ? rows.size() : 0;
    }

    private List<?> displayRows(List<?> rows, AiIntentResult route, int defaultLimit) {
        return wantsFullList(route) ? rows : limit(rows, defaultLimit);
    }

    private boolean wantsFullList(AiIntentResult route) {
        return containsAny(routeQuery(route), "tat ca", "toan bo", "day du", "all");
    }

    private String continuationSuffix(long total, int displayed, int loaded, String noun,
            String fullListPrompt, String filterHint) {
        StringBuilder suffix = new StringBuilder();
        if (total > displayed) {
            suffix.append("\n\n*Đang hiển thị ").append(formatNumber(displayed)).append(" ")
                    .append(noun).append(" tiêu biểu. Bạn có thể nhắn: \"")
                    .append(fullListPrompt).append("\" để xem ");
            if (total > loaded) {
                suffix.append("toàn bộ ").append(formatNumber(loaded)).append(" ")
                        .append(noun).append(" đã tải.");
            } else {
                suffix.append("đủ ").append(formatNumber(total)).append(" ")
                        .append(noun).append(" đã tìm thấy.");
            }
            suffix.append("*");
        }
        if (loaded >= 50 && total >= loaded) {
            suffix.append("\n\n*Lưu ý: kết quả truy vấn hiện có thể đang giới hạn ")
                    .append(formatNumber(loaded)).append(" ").append(noun)
                    .append(" đầu tiên. Nếu cần xem sâu hơn, hãy ")
                    .append(filterHint).append(".*");
        }
        return suffix.toString();
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
            List<Map<String, String>> history, AiQueryContext context) {
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
                Data sources JSON: %s
                Missing params JSON: %s
                Row count: %s
                Tool result JSON: %s
                <|im_end|>
                <|im_start|>assistant
                """.formatted(
                toJson(compactHistory(history)),
                userMessage,
                route == null ? "UNSUPPORTED" : route.getIntent(),
                toJson(route == null ? Map.of() : route.safeParameters()),
                toolResult == null ? "none" : toolResult.toolName(),
                toJson(context == null ? List.of() : context.dataSources()),
                toJson(context == null ? List.of() : context.missingParams()),
                context == null ? 0 : context.rowCount(),
                toJson(compactToolResultData(toolResult == null ? null : toolResult.data()))
        );
    }

    private int estimateRows(AiToolResult result) {
        if (result == null || result.data() == null) {
            return 0;
        }
        if (result.data() instanceof List<?> list) {
            return list.size();
        }
        if (result.data() instanceof Map<?, ?> map) {
            return map.size();
        }
        return 1;
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
