package com.ai_service.service.conversation;

import com.ai_service.context.AiQueryContext;
import com.ai_service.context.AiQualityAssessment;
import com.ai_service.dto.AiAskResponse.AiActionSuggestion;
import com.ai_service.dto.AiAskResponse.AiResponseMetadata;
import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentCatalog;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiResponseEnrichmentService {

    public AiResponseMetadata build(AiIntentResult route, AiToolResult toolResult, AiQueryContext context) {
        AiIntent intent = route == null || route.getIntent() == null ? AiIntent.UNSUPPORTED : route.getIntent();
        Map<String, Object> parameters = route == null ? Map.of() : route.safeParameters();
        List<String> missing = context == null ? missingParams(toolResult) : context.missingParams();
        int rowsReturned = context == null ? estimateRows(toolResult) : context.rowCount();
        AiQualityAssessment quality = context == null
                ? AiQualityAssessment.from(route, toolResult, missing, rowsReturned)
                : context.quality();
        return new AiResponseMetadata(
                intent.name(),
                route == null ? 0.0 : route.getConfidence(),
                context == null ? AiIntentCatalog.get(intent).domain() : context.domain(),
                context == null ? toolName(toolResult) : context.toolName(),
                context == null ? dataSources(toolResult) : context.dataSources(),
                missing,
                rowsReturned,
                sanitizedParameters(parameters),
                suggestedQuestions(intent, context),
                suggestedActions(intent, parameters, toolResult),
                quality.intentQuality(),
                quality.needsClarification(),
                quality.clarificationReason(),
                quality.qualitySignals(),
                displayMetadata(intent, toolResult),
                resultRows(toolResult, 50),
                mapListFromUiMetadata(toolResult, "candidateSuggestions")
        );
    }

    private List<String> suggestedQuestions(AiIntent intent, AiQueryContext context) {
        if (context != null && context.missingParams() != null && !context.missingParams().isEmpty()) {
            return List.of(
                    "Bạn muốn kiểm tra mã kho, mã hàng hay mã đơn nào?",
                    "Bạn có muốn lọc theo hôm nay, tuần này hoặc tháng này không?"
            );
        }
        return switch (intent) {
            case STOCK_BY_PRODUCT -> List.of(
                    "Sản phẩm này ở kho nào nhiều nhất?",
                    "Lịch sử biến động 7 ngày qua?",
                    "Có cần nhập bổ sung không?"
            );
            case LOW_STOCK, REORDER_SUGGESTION -> List.of(
                    "Sản phẩm nào cần nhập bổ sung trước?",
                    "Lọc theo từng kho được không?",
                    "Có phiếu nhập nào đang chờ nhận cho các sản phẩm này không?"
            );
            case NEAR_EXPIRY, STOCK_AT_RISK -> List.of(
                    "Lô nào cần xử lý trước?",
                    "Các lô này đang nằm ở vị trí nào?",
                    "Có đơn xuất nào có thể ưu tiên dùng các lô này không?"
            );
            case OUTBOUND_PRIORITY, OUTBOUND_DELAYED, PICKING_STATUS -> List.of(
                    "Đơn nào gần deadline nhất?",
                    "Dòng nào còn thiếu hàng?",
                    "Ai đang phụ trách lấy hàng?"
            );
            case PENDING_PUTAWAY, INBOUND_PENDING_PUTAWAY -> List.of(
                    "Task nào chờ lâu nhất?",
                    "Kho nào đang tồn đọng hàng chờ xếp kệ nhiều nhất?",
                    "Có vị trí gợi ý cho các việc này không?"
            );
            case PURCHASE_ORDER_STATUS, PURCHASE_ORDER_DETAIL, PENDING_PO_RECEIPT -> List.of(
                    "PO này còn dòng nào chưa nhận đủ?",
                    "Ai đã duyệt phiếu nhập này?",
                    "Nhà cung cấp giao đúng hạn không?"
            );
            case SALES_ORDER_STATUS, SALES_ORDER_DETAIL -> List.of(
                    "Đơn này còn thiếu dòng nào?",
                    "Trạng thái lấy hàng của đơn này?",
                    "Có nguy cơ trễ giao không?"
            );
            case DAILY_TASKS, REPORT_SUMMARY, FLOW_REPORT -> List.of(
                    "Việc nào cần ưu tiên hôm nay?",
                    "Có cảnh báo tồn thấp không?",
                    "Có đơn xuất nào đang trễ không?"
            );
            case AMBIGUOUS -> List.of(
                    "Bạn muốn hỏi về tồn kho, nhập kho hay xuất kho?",
                    "Bạn có mã hàng, mã phiếu nhập, mã đơn xuất hoặc kho cụ thể không?"
            );
            default -> List.of();
        };
    }

    private List<AiActionSuggestion> suggestedActions(AiIntent intent, Map<String, Object> parameters,
            AiToolResult toolResult) {
        List<AiActionSuggestion> actions = new ArrayList<>();
        if (intent == AiIntent.STOCK_TRANSFER) {
            actions.add(new AiActionSuggestion(
                    "CREATE_STOCK_TRANSFER_DRAFT",
                    "Tạo đề xuất chuyển kho",
                    "Tạo phiếu chuyển kho dạng nháp, chưa thay đổi tồn kho cho đến khi người có quyền xác nhận.",
                    true,
                    "WAREHOUSE_MANAGER",
                    sanitizedParameters(parameters)
            ));
        }
        if (intent == AiIntent.INVENTORY_ADJUSTMENT) {
            actions.add(new AiActionSuggestion(
                    "CREATE_INVENTORY_ADJUSTMENT_DRAFT",
                    "Tạo đề xuất điều chỉnh tồn",
                    "Tạo phiếu điều chỉnh tồn dạng nháp để quản lý kiểm tra và xác nhận.",
                    true,
                    "WAREHOUSE_MANAGER",
                    sanitizedParameters(parameters)
            ));
        }
        if (intent == AiIntent.REORDER_SUGGESTION || intent == AiIntent.LOW_STOCK) {
            actions.add(new AiActionSuggestion(
                    "REVIEW_REORDER_SUGGESTIONS",
                    "Xem gợi ý nhập bổ sung",
                    "Mở danh sách sản phẩm dưới định mức để kiểm tra trước khi tạo phiếu nhập.",
                    false,
                    "WAREHOUSE_MANAGER",
                    sanitizedParameters(parameters)
            ));
        }
        if (intent == AiIntent.LOW_STOCK) {
            List<String> skus = skuListFromResult(toolResult, 50);
            Map<String, Object> payload = new LinkedHashMap<>(sanitizedParameters(parameters));
            payload.put("actionType", "MARK_PRODUCTS_OUT_OF_STOCK");
            payload.put("source", "LOW_STOCK_RESULT");
            payload.put("targetStatus", "OUT_OF_STOCK");
            payload.put("skuList", skus);
            actions.add(new AiActionSuggestion(
                    "MARK_PRODUCTS_OUT_OF_STOCK",
                    "Tạm ngừng bán sản phẩm hết hàng",
                    "Xem trước danh sách sản phẩm tồn thấp/hết hàng rồi xác nhận chuyển sang trạng thái tạm ngừng bán.",
                    true,
                    "WAREHOUSE_MANAGER",
                    payload
            ));
        }
        return actions;
    }

    private List<String> skuListFromResult(AiToolResult result, int limit) {
        return resultRows(result, limit).stream()
                .map(row -> row.get("sku"))
                .filter(value -> value != null && !String.valueOf(value).isBlank())
                .map(value -> String.valueOf(value).trim())
                .distinct()
                .toList();
    }

    private Map<String, Object> sanitizedParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>(parameters);
        safe.remove("apiKey");
        safe.remove("token");
        safe.remove("password");
        safe.remove("secret");
        return safe;
    }

    private String toolName(AiToolResult result) {
        return result == null ? "none" : result.toolName();
    }

    private List<String> dataSources(AiToolResult result) {
        return result == null || result.dataSources() == null ? List.of() : result.dataSources();
    }

    private List<String> missingParams(AiToolResult result) {
        return result == null || result.missingParams() == null ? List.of() : result.missingParams();
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

    private Map<String, Object> displayMetadata(AiIntent intent, AiToolResult result) {
        Map<String, Object> explicit = mapFromUiMetadata(result, "display");
        if (!explicit.isEmpty()) {
            return explicit;
        }
        if (result == null || !result.dataBacked()) {
            return Map.of("type", "text");
        }
        return switch (intent) {
            case STOCK_BY_PRODUCT -> Map.of(
                    "type", "table",
                    "title", "Tồn kho sản phẩm",
                    "columns", List.of("sku", "product_name", "warehouse_code", "qty_on_hand", "qty_reserved", "qty_available"));
            case NEAR_EXPIRY -> Map.of(
                    "type", "table",
                    "title", "Lô sắp hết hạn",
                    "columns", List.of("product_name", "warehouse_code", "location_code", "lot_number", "expiry_date", "days_left", "qty_on_hand"));
            case PRODUCT_LIST, PRODUCT_BY_CATEGORY -> Map.of(
                    "type", "table",
                    "title", "Danh sách sản phẩm",
                    "columns", List.of("sku", "product_name", "category_name", "status", "qty_on_hand"));
            case CATEGORY_LIST -> Map.of(
                    "type", "list",
                    "title", "Danh mục sản phẩm",
                    "columns", List.of("code", "name", "product_count"));
            case SUPPLIER_LIST, SUPPLIER_SEARCH -> Map.of(
                    "type", "table",
                    "title", "Nhà cung cấp",
                    "columns", List.of("code", "name", "status", "contact_name"));
            case CUSTOMER_LIST, CUSTOMER_SEARCH -> Map.of(
                    "type", "table",
                    "title", "Khách hàng",
                    "columns", List.of("code", "name", "is_active", "contact_name"));
            case LOCATION_SEARCH -> Map.of(
                    "type", "table",
                    "title", "Vị trí kho",
                    "columns", List.of("warehouse_code", "code", "zone", "location_type", "status"));
            case USER_LOOKUP -> Map.of(
                    "type", "table",
                    "title", "Người dùng",
                    "columns", List.of("username", "full_name", "roles", "warehouses"));
            case ROLE_LIST -> Map.of(
                    "type", "list",
                    "title", "Vai trò",
                    "columns", List.of("code", "name", "user_count"));
            default -> Map.of("type", "text");
        };
    }

    private List<Map<String, Object>> resultRows(AiToolResult result, int limit) {
        if (result == null || result.data() == null) {
            return List.of();
        }
        Object data = result.data();
        if (data instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(row -> sanitizeRow((Map<?, ?>) row))
                    .limit(Math.max(0, limit))
                    .toList();
        }
        if (data instanceof Map<?, ?> map && map.get("items") instanceof List<?> items) {
            return items.stream()
                    .filter(Map.class::isInstance)
                    .map(row -> sanitizeRow((Map<?, ?>) row))
                    .limit(Math.max(0, limit))
                    .toList();
        }
        return List.of();
    }

    private Map<String, Object> sanitizeRow(Map<?, ?> row) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : row.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            if (isSensitiveKey(key)) {
                continue;
            }
            safe.put(key, entry.getValue());
        }
        return safe;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase();
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("apikey")
                || normalized.contains("api_key");
    }

    private Map<String, Object> mapFromUiMetadata(AiToolResult result, String key) {
        if (result == null || result.uiMetadata() == null) {
            return Map.of();
        }
        Object value = result.uiMetadata().get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return sanitizeRow(map);
    }

    private List<Map<String, Object>> mapListFromUiMetadata(AiToolResult result, String key) {
        if (result == null || result.uiMetadata() == null) {
            return List.of();
        }
        Object value = result.uiMetadata().get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(row -> sanitizeRow((Map<?, ?>) row))
                .toList();
    }
}
