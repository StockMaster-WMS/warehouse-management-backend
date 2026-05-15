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

    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    // Tạo câu trả lời AI dạng đồng bộ từ route và tool result.
    public String compose(String userMessage, AiIntentResult route, AiToolResult toolResult,
            List<Map<String, String>> history) {
        String deterministic = deterministicReply(route, toolResult);
        if (deterministic != null) {
            return deterministic;
        }
        return ollamaClient.generateAnswer(buildAnswerPrompt(userMessage, route, toolResult, history));
    }

    // Tạo câu trả lời AI dạng stream từ route và tool result.
    public void composeStream(String userMessage, AiIntentResult route, AiToolResult toolResult,
            List<Map<String, String>> history, Consumer<String> fragmentConsumer) {
        String deterministic = deterministicReply(route, toolResult);
        if (deterministic != null) {
            fragmentConsumer.accept(deterministic);
            return;
        }
        ollamaClient.generateAnswerStream(buildAnswerPrompt(userMessage, route, toolResult, history), fragmentConsumer);
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
                case WAREHOUSE_STOCK_SUMMARY -> "Hiện chưa có dữ liệu tồn kho phù hợp với câu hỏi này.";
                case LOCATION_SEARCH -> "Tôi chưa tìm thấy vị trí kho phù hợp với điều kiện này.";
                case PENDING_PUTAWAY -> "Hiện chưa có task putaway nào đang chờ xử lý.";
                case LATEST_INBOUND -> "Hiện chưa có phiếu nhập kho nào trong dữ liệu.";
                case PURCHASE_ORDER_STATUS -> "Tôi chưa tìm thấy đơn nhập phù hợp.";
                case OUTBOUND_PRIORITY -> "Hiện chưa có đơn xuất nào trong nhóm cần ưu tiên theo dữ liệu hiện tại.";
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
                case STOCK_TOTAL -> "Tổng tồn kho hiện có: " + value(map, "qty_on_hand") + " đơn vị, đã giữ chỗ "
                        + value(map, "qty_reserved") + ", khả dụng " + value(map, "qty_available")
                        + " trên " + value(map, "stocked_skus") + " SKU có tồn.";
                case DAILY_TASKS -> dailyTasksReply(map);
                case REPORT_SUMMARY -> reportSummaryReply(map);
                default -> null;
            };
        }
        if (data instanceof List<?> list) {
            return switch (route.getIntent()) {
                case WAREHOUSE_LIST -> warehouseListReply(list);
                case WAREHOUSE_DETAIL -> warehouseDetailReply(list);
                case STOCK_BY_PRODUCT -> stockByProductReply(list);
                case STOCK_LOWEST -> stockLowestReply(list);
                case LOW_STOCK -> lowStockReply(list);
                case WAREHOUSE_STOCK_SUMMARY -> warehouseStockSummaryReply(list);
                case NEAR_EXPIRY -> nearExpiryReply(list);
                case PENDING_PUTAWAY -> putawayReply(list);
                case LATEST_INBOUND -> latestInboundReply(list);
                case PURCHASE_ORDER_STATUS -> purchaseOrderReply(list);
                case OUTBOUND_PRIORITY -> outboundPriorityReply(list);
                case PICKING_STATUS -> pickingReply(list);
                case ACTIVE_CYCLE_COUNTS -> activeCycleCountReply(list);
                case CYCLE_COUNT_VARIANCE -> cycleVarianceReply(list);
                case RMA_PENDING -> rmaReply(list);
                case LOCATION_SEARCH -> locationReply(list);
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

    private String outboundPriorityReply(List<?> list) {
        Object first = list.get(0);
        return "Đơn xuất ưu tiên cao nhất hiện tại là " + value(first, "so_number")
                + " của " + value(first, "customer_name")
                + ", priority " + value(first, "priority")
                + ", trạng thái " + value(first, "status") + ".";
    }

    private String pickingReply(List<?> list) {
        return "Có " + list.size() + " picking task/dòng picking phù hợp: " + joinRows(limit(list, 6), row ->
                value(row, "so_number") + " - " + value(row, "product_name")
                        + ", cần pick " + value(row, "qty_to_pick")
                        + ", đã pick " + value(row, "qty_picked")
                        + ", trạng thái " + value(row, "status"));
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

    private String joinRows(List<?> rows, java.util.function.Function<Object, String> formatter) {
        return rows.stream()
                .map(formatter)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
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
                toJson(history),
                userMessage,
                route == null ? "UNSUPPORTED" : route.getIntent(),
                toJson(route == null ? Map.of() : route.safeParameters()),
                toolResult == null ? "none" : toolResult.toolName(),
                toJson(toolResult == null ? null : toolResult.data())
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
}
