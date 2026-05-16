package com.ai_service.service;

import com.ai_service.client.OllamaClient;
import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnswerComposerService {

    private static final int MAX_HISTORY_MESSAGES = 4;
    private static final int MAX_TOOL_LIST_ITEMS = 5;
    private static final int MAX_TOOL_TEXT_LENGTH = 2000;
    private final OllamaClient ollamaClient;
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
        log.info("AI compose mode=ollama start intent={} tool={}",
                route == null ? "null" : route.getIntent(),
                toolResult == null ? "null" : toolResult.toolName());
        String answer = ollamaClient.generateAnswer(buildAnswerPrompt(userMessage, route, toolResult, history));
        log.info("AI compose mode=ollama done intent={} tool={} outputChars={} durationMs={}",
                route == null ? "null" : route.getIntent(),
                toolResult == null ? "null" : toolResult.toolName(),
                answer == null ? 0 : answer.length(), System.currentTimeMillis() - start);
        return answer;
    }

    // Tạo câu trả lời AI dạng stream từ route và tool result.
        public void composeStream(String userMessage, AiIntentResult route, AiToolResult toolResult,
            List<Map<String, String>> history, Consumer<String> fragmentConsumer, java.util.function.Supplier<Boolean> isCancelled) {
        long start = System.currentTimeMillis();
        String deterministic = deterministicReply(route, toolResult);
        if (deterministic != null) {
            log.info("AI composeStream mode=deterministic intent={} tool={} outputChars={} durationMs={}",
                    route == null ? "null" : route.getIntent(),
                    toolResult == null ? "null" : toolResult.toolName(),
                    deterministic.length(), System.currentTimeMillis() - start);
            fragmentConsumer.accept(deterministic);
            return;
        }
        log.info("AI composeStream mode=ollama start intent={} tool={}",
                route == null ? "null" : route.getIntent(),
                toolResult == null ? "null" : toolResult.toolName());
        ollamaClient.generateAnswerStream(buildAnswerPrompt(userMessage, route, toolResult, history), fragmentConsumer, isCancelled);
        log.info("AI composeStream mode=ollama done intent={} tool={} cancelled={} durationMs={}",
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
                case PRODUCT_BY_BARCODE -> "Tôi chưa tìm thấy sản phẩm hoặc tồn kho theo barcode bạn hỏi.";
                case STOCK_BELOW_THRESHOLD -> "Hiện chưa có sản phẩm nào dưới ngưỡng tồn kho bạn hỏi.";
                case WAREHOUSE_STOCK_SUMMARY -> "Hiện chưa có dữ liệu tồn kho phù hợp với câu hỏi này.";
                case LOCATION_SEARCH -> "Tôi chưa tìm thấy vị trí kho phù hợp với điều kiện này.";
                case BEST_HEAVY_LOCATION -> "Hiện chưa có vị trí phù hợp cho hàng nặng theo dữ liệu hiện tại.";
                case PENDING_PUTAWAY -> "Hiện chưa có task putaway nào đang chờ xử lý.";
                case PUTAWAY_BY_WAREHOUSE -> "Hiện chưa có putaway task đang chờ để tổng hợp theo kho.";
                case INBOUND_TODAY -> "Hôm nay chưa có lô hàng/phiếu nhập kho nào được ghi nhận.";
                case LATEST_INBOUND -> "Hiện chưa có phiếu nhập kho nào trong dữ liệu.";
                case PENDING_PO_RECEIPT -> "Hiện chưa có PO nào đang chờ nhận hàng.";
                case PURCHASE_ORDER_STATUS -> "Tôi chưa tìm thấy đơn nhập phù hợp.";
                case OUTBOUND_PRIORITY -> "Hiện chưa có đơn xuất nào trong nhóm cần ưu tiên theo dữ liệu hiện tại.";
                case PACKING_STATUS -> "Hiện chưa có đơn xuất nào đang chờ packing.";
                case PICKING_TOP -> "Hiện chưa có dữ liệu picking để xếp hạng sản phẩm.";
                case PICKING_STATUS -> "Hiện chưa có dòng picking nào đang mở.";
                case ACTIVE_CYCLE_COUNTS -> "Hiện không có lịch kiểm kê cycle count nào đang diễn ra.";
                case CYCLE_COUNT_VARIANCE -> "Hiện chưa ghi nhận dòng kiểm kê đang lệch tồn hoặc đang chờ đếm.";
                case RMA_PENDING -> "Hiện không có yêu cầu trả hàng RMA nào đang chờ xử lý.";
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
            return switch (route.getIntent()) {
                case PRODUCT_COUNT -> "Hệ thống hiện có " + value(map, "total") + " sản phẩm.";
                case LOCATION_COUNT -> locationCountReply(map);
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
                case OUTBOUND_REPORT -> outboundReportReply(map);
                case MONTHLY_REPORT -> monthlyReportReply(map);
                default -> null;
            };
        }
        if (data instanceof List<?> list) {
            return switch (route.getIntent()) {
                case WAREHOUSE_LIST -> warehouseListReply(list);
                case WAREHOUSE_DETAIL -> warehouseDetailReply(list);
                case PRODUCT_DETAIL -> productDetailReply(list);
                case SUPPLIER_DETAIL -> supplierDetailReply(list);
                case CUSTOMER_DETAIL -> customerDetailReply(list);
                case STOCK_BY_PRODUCT -> stockByProductReply(list);
                case STOCK_LOWEST -> stockLowestReply(list);
                case STOCK_HIGHEST -> stockHighestReply(list);
                case PRODUCT_BY_BARCODE -> productByBarcodeReply(list);
                case STOCK_BELOW_THRESHOLD -> stockBelowThresholdReply(list);
                case LOW_STOCK -> lowStockReply(list);
                case WAREHOUSE_STOCK_SUMMARY -> warehouseStockSummaryReply(list);
                case BEST_HEAVY_LOCATION -> heavyLocationReply(list);
                case NEAR_EXPIRY -> nearExpiryReply(list);
                case PENDING_PUTAWAY -> putawayReply(list);
                case PUTAWAY_BY_WAREHOUSE -> putawayByWarehouseReply(list);
                case INBOUND_TODAY -> inboundTodayReply(list);
                case LATEST_INBOUND -> latestInboundReply(list);
                case PENDING_PO_RECEIPT -> pendingPoReceiptReply(list);
                case PURCHASE_ORDER_STATUS -> purchaseOrderReply(list);
                case PURCHASE_ORDER_DETAIL -> purchaseOrderDetailReply(list);
                case OUTBOUND_PRIORITY -> outboundPriorityReply(list);
                case PACKING_STATUS -> packingReply(list);
                case PICKING_TOP -> pickingTopReply(list);
                case PICKING_STATUS -> pickingReply(list);
                case SALES_TOP -> salesTopReply(list);
                case SALES_ORDER_STATUS -> salesOrderStatusReply(list);
                case SALES_ORDER_DETAIL -> salesOrderDetailReply(list);
                case STOCK_MOVEMENT_HISTORY -> stockMovementReply(list);
                case ACTIVE_CYCLE_COUNTS -> activeCycleCountReply(list);
                case CYCLE_COUNT_VARIANCE -> cycleVarianceReply(list);
                case RMA_PENDING -> rmaReply(list);
                case LOCATION_SEARCH -> locationReply(list);
                case AUDIT_LOG -> auditLogReply(list);
                case AI_AUDIT_LOG -> aiAuditLogReply(list);
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

    private String locationCountReply(Map<?, ?> map) {
        if ("false".equalsIgnoreCase(value(map, "warehouse_found"))) {
            return "Tôi chưa tìm thấy kho phù hợp với thông tin bạn nêu.";
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
                + " (" + value(row, "category_name") + ", " + value(row, "status") + ")");
    }

    private String supplierListReply(Map<?, ?> map) {
        Object itemsObject = map.get("items");
        if (!(itemsObject instanceof List<?> items) || items.isEmpty()) {
            return "Tôi chưa tìm thấy nhà cung cấp phù hợp.";
        }
        return "Tìm thấy " + value(map, "total") + " nhà cung cấp. Một số dòng: "
                + joinRows(limit(items, 6), row -> value(row, "code") + " - " + value(row, "name")
                + " (" + value(row, "status") + ", liên hệ " + value(row, "contact_name") + ")");
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
                + ", khả dụng " + value(row, "qty_available") + ", trạng thái " + value(row, "status"));
    }

    private String supplierDetailReply(List<?> list) {
        return "Chi tiết nhà cung cấp: " + joinRows(limit(list, 5), row -> value(row, "code") + " - "
                + value(row, "name") + ", liên hệ " + value(row, "contact_name")
                + ", điện thoại " + value(row, "contact_phone") + ", email " + value(row, "contact_email")
                + ", trạng thái " + value(row, "status"));
    }

    private String customerDetailReply(List<?> list) {
        return "Chi tiết khách hàng: " + joinRows(limit(list, 5), row -> value(row, "code") + " - "
                + value(row, "name") + ", liên hệ " + value(row, "contact_name")
                + ", điện thoại " + value(row, "phone") + ", email " + value(row, "email")
                + ", trạng thái " + activeText(value(row, "is_active")));
    }

    private String stockByProductReply(List<?> list) {
        return "Tìm thấy " + list.size() + " dòng tồn kho: " + joinRows(list, row ->
                value(row, "product_name") + " tại " + value(row, "warehouse_code")
                        + " còn " + value(row, "qty_on_hand") + ", giữ chỗ "
                        + value(row, "qty_reserved") + ", khả dụng " + value(row, "qty_available"));
    }

    private String stockLowestReply(List<?> list) {
        return "Các sản phẩm có tồn khả dụng thấp nhất: " + joinRows(limit(list, 5), row ->
                value(row, "product_name") + " còn khả dụng " + value(row, "qty_available")
                        + " (tồn hiện có " + value(row, "qty_on_hand") + ", định mức "
                        + value(row, "min_stock_qty") + ")");
    }

    private String stockHighestReply(List<?> list) {
        Object first = list.get(0);
        return "Sản phẩm có tồn kho cao nhất là " + value(first, "product_name")
                + " với tồn hiện có " + value(first, "qty_on_hand")
                + ", khả dụng " + value(first, "qty_available") + ". Top: "
                + joinRows(limit(list, 5), row -> value(row, "product_name") + " tồn " + value(row, "qty_on_hand"));
    }

    private String productByBarcodeReply(List<?> list) {
        return joinRows(list, row -> "Barcode " + value(row, "barcode_ean13") + " là "
                + value(row, "product_name") + ", tồn hiện có " + value(row, "qty_on_hand")
                + ", giữ chỗ " + value(row, "qty_reserved") + ", khả dụng " + value(row, "qty_available"));
    }

    private String stockBelowThresholdReply(List<?> list) {
        return "Có " + list.size() + " sản phẩm dưới ngưỡng tồn kho yêu cầu. Một số dòng: "
                + joinRows(limit(list, 8), row -> value(row, "product_name") + " tồn " + value(row, "qty_on_hand"));
    }

    private String lowStockReply(List<?> list) {
        return "Có " + list.size() + " sản phẩm đang dưới định mức. Một số dòng thấp nhất: "
                + joinRows(limit(list, 5), row -> value(row, "product_name") + " khả dụng "
                + value(row, "qty_available") + " / định mức " + value(row, "min_stock_qty"));
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
        return "Có " + list.size() + " lô hàng sắp hết hạn: " + joinRows(limit(list, 5), row ->
                value(row, "product_name") + " tại " + value(row, "warehouse_code")
                        + ", hạn " + value(row, "expiry_date") + ", còn " + value(row, "days_left") + " ngày");
    }

    private String putawayReply(List<?> list) {
        return "Có " + list.size() + " putaway task đang chờ/xử lý: " + joinRows(limit(list, 6), row ->
                value(row, "product_name") + " số lượng " + value(row, "qty_to_putaway")
                        + ", trạng thái " + value(row, "status")
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

    private String purchaseOrderReply(List<?> list) {
        return "Tìm thấy " + list.size() + " đơn nhập: " + joinRows(limit(list, 5), row ->
                value(row, "po_number") + " trạng thái " + value(row, "status")
                        + ", NCC " + value(row, "supplier_name")
                        + ", dự kiến " + value(row, "expected_date"));
    }

    private String purchaseOrderDetailReply(List<?> list) {
        Object first = list.get(0);
        return "PO " + value(first, "po_number") + " trạng thái " + value(first, "status")
                + ", NCC " + value(first, "supplier_name") + ", kho " + value(first, "warehouse_code")
                + ". Dòng hàng: " + joinRows(limit(list, 8), row -> "#" + value(row, "line_number")
                + " " + value(row, "sku") + " đặt " + value(row, "ordered_qty")
                + ", đã nhận " + value(row, "received_qty"));
    }

    private String pendingPoReceiptReply(List<?> list) {
        return "Có " + list.size() + " PO đang chờ nhận/chưa hoàn tất: " + joinRows(limit(list, 6), row ->
                value(row, "po_number") + " trạng thái " + value(row, "status")
                        + ", còn phải nhận " + value(row, "remaining_qty")
                        + ", NCC " + value(row, "supplier_name"));
    }

    private String outboundPriorityReply(List<?> list) {
        Object first = list.get(0);
        return "Đơn xuất ưu tiên cao nhất hiện tại là " + value(first, "so_number")
                + " của " + value(first, "customer_name")
                + ", priority " + value(first, "priority")
                + ", trạng thái " + value(first, "status") + ".";
    }

    private String salesOrderStatusReply(List<?> list) {
        return "Tìm thấy " + list.size() + " đơn xuất: " + joinRows(limit(list, 6), row ->
                value(row, "so_number") + " trạng thái " + value(row, "status")
                        + ", khách " + value(row, "customer_name")
                        + ", đặt " + value(row, "ordered_qty")
                        + ", đã giao " + value(row, "shipped_qty"));
    }

    private String salesOrderDetailReply(List<?> list) {
        Object first = list.get(0);
        return "SO " + value(first, "so_number") + " trạng thái " + value(first, "status")
                + ", khách " + value(first, "customer_name") + ", kho " + value(first, "warehouse_code")
                + ". Dòng hàng: " + joinRows(limit(list, 8), row -> "#" + value(row, "line_number")
                + " " + value(row, "sku") + " đặt " + value(row, "ordered_qty")
                + ", đã giao " + value(row, "shipped_qty"));
    }

    private String packingReply(List<?> list) {
        return "Có " + list.size() + " đơn xuất đang ở bước trước/trong packing: " + joinRows(limit(list, 6), row ->
                value(row, "so_number") + " trạng thái " + value(row, "status")
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

    private String pickingReply(List<?> list) {
        return "Có " + list.size() + " picking task/dòng picking phù hợp: " + joinRows(limit(list, 6), row ->
                value(row, "so_number") + " - " + value(row, "product_name")
                        + ", cần pick " + value(row, "qty_to_pick")
                        + ", đã pick " + value(row, "qty_picked")
                        + ", trạng thái " + value(row, "status"));
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
                        + ", trạng thái " + value(row, "status")
                        + ", số dòng " + value(row, "item_count"));
    }

    private String cycleVarianceReply(List<?> list) {
        return "Có " + list.size() + " dòng kiểm kê đang lệch hoặc chờ đếm: " + joinRows(limit(list, 6), row ->
                value(row, "cycle_count_id") + " - " + value(row, "product_name")
                        + ", hệ thống " + value(row, "system_qty")
                        + ", đếm " + value(row, "counted_qty")
                        + ", chênh lệch " + value(row, "discrepancy"));
    }

    private String rmaReply(List<?> list) {
        return "Có " + list.size() + " yêu cầu RMA đang chờ xử lý: " + joinRows(limit(list, 5), row ->
                value(row, "rma_number") + " - " + value(row, "customer_name")
                        + ", trạng thái " + value(row, "status"));
    }

    private String locationReply(List<?> list) {
        return "Tìm thấy " + list.size() + " vị trí: " + joinRows(limit(list, 8), row ->
                value(row, "warehouse_code") + "/" + value(row, "code")
                        + " zone " + value(row, "zone")
                        + ", trạng thái " + value(row, "status"));
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
        return "Hôm nay cần chú ý: putaway chờ " + nestedValue(map, "pending_putaway", "total")
                + ", picking pending " + nestedValue(map, "pending_picking", "total")
                + ", cycle count đang mở " + nestedValue(map, "active_cycle_counts", "total")
                + ", lô sắp hết hạn trong 7 ngày " + nestedValue(map, "near_expiry_7_days", "total")
                + ", RMA chờ xử lý " + nestedValue(map, "pending_rma", "total") + ".";
    }

    private String reportSummaryReply(Map<?, ?> map) {
        return "Tổng quan vận hành: " + nestedValue(map, "warehouses", "active") + " kho đang hoạt động, tồn hiện có "
                + nestedValue(map, "stock", "qty_on_hand") + ", giữ chỗ " + nestedValue(map, "stock", "qty_reserved")
                + ", putaway chờ " + nestedValue(map, "pending_putaway", "total")
                + ", đơn xuất cần xử lý " + nestedValue(map, "priority_outbound", "total") + ".";
    }

    private String flowReportReply(Map<?, ?> map) {
        return "Báo cáo nhập - xuất 7 ngày qua: phiếu nhập "
                + nestedValue(map, "inbound", "receipts") + ", số lượng nhập "
                + nestedValue(map, "inbound", "received_qty") + "; đơn bán "
                + nestedValue(map, "outbound", "sales_orders") + ", số lượng đặt "
                + nestedValue(map, "outbound", "ordered_qty") + ", đã giao "
                + nestedValue(map, "outbound", "shipped_qty") + "; stock movement "
                + nestedValue(map, "stock_movements", "movements") + ".";
    }

    private String inboundReportReply(Map<?, ?> map) {
        return "Báo cáo nhập kho: phiếu nhập " + nestedValue(map, "summary", "receipts")
                + ", PO " + nestedValue(map, "summary", "purchase_orders")
                + ", số lượng nhận " + nestedValue(map, "summary", "received_qty")
                + ". Top NCC: " + joinRowsFromMapList(map, "top_suppliers", row ->
                value(row, "supplier_name") + " nhận " + value(row, "received_qty"));
    }

    private String outboundReportReply(Map<?, ?> map) {
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

    private String globalSearchReply(Map<?, ?> map) {
        return "Kết quả tìm kiếm: sản phẩm " + listSize(map, "products")
                + ", kho " + listSize(map, "warehouses")
                + ", NCC " + listSize(map, "suppliers")
                + ", khách hàng " + listSize(map, "customers")
                + ", PO " + listSize(map, "purchase_orders")
                + ", SO " + listSize(map, "sales_orders") + ".";
    }

    private String joinRows(List<?> rows, java.util.function.Function<Object, String> formatter) {
        return rows.stream()
                .map(formatter)
                .reduce((a, b) -> a + "; " + b)
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
