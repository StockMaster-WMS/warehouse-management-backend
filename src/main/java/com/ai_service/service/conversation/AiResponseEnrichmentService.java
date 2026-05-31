package com.ai_service.service.conversation;

import com.ai_service.context.AiQueryContext;
import com.ai_service.dto.AiAskResponse.AiActionSuggestion;
import com.ai_service.dto.AiAskResponse.AiResponseMetadata;
import com.ai_service.intent.AiIntent;
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
        return new AiResponseMetadata(
                intent.name(),
                route == null ? 0.0 : route.getConfidence(),
                context == null ? toolName(toolResult) : context.toolName(),
                context == null ? dataSources(toolResult) : context.dataSources(),
                context == null ? missingParams(toolResult) : context.missingParams(),
                context == null ? estimateRows(toolResult) : context.rowCount(),
                sanitizedParameters(parameters),
                suggestedQuestions(intent, context),
                suggestedActions(intent, parameters)
        );
    }

    private List<String> suggestedQuestions(AiIntent intent, AiQueryContext context) {
        if (context != null && context.missingParams() != null && !context.missingParams().isEmpty()) {
            return List.of(
                    "Bạn muốn kiểm tra mã kho, SKU hay mã đơn nào?",
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
                    "SKU nào cần nhập bổ sung trước?",
                    "Lọc theo từng kho được không?",
                    "Có PO nào đang chờ nhận cho các SKU này không?"
            );
            case NEAR_EXPIRY, STOCK_AT_RISK -> List.of(
                    "Lô nào cần xử lý trước?",
                    "Các lô này đang nằm ở vị trí nào?",
                    "Có đơn xuất nào có thể ưu tiên dùng các lô này không?"
            );
            case OUTBOUND_PRIORITY, OUTBOUND_DELAYED, PICKING_STATUS -> List.of(
                    "Đơn nào gần deadline nhất?",
                    "Dòng nào còn thiếu hàng?",
                    "Ai đang phụ trách picking?"
            );
            case PENDING_PUTAWAY, INBOUND_PENDING_PUTAWAY -> List.of(
                    "Task nào chờ lâu nhất?",
                    "Kho nào đang tồn đọng putaway nhiều nhất?",
                    "Có vị trí gợi ý cho các task này không?"
            );
            case PURCHASE_ORDER_STATUS, PURCHASE_ORDER_DETAIL, PENDING_PO_RECEIPT -> List.of(
                    "PO này còn dòng nào chưa nhận đủ?",
                    "Ai đã duyệt phiếu nhập này?",
                    "Nhà cung cấp giao đúng hạn không?"
            );
            case SALES_ORDER_STATUS, SALES_ORDER_DETAIL -> List.of(
                    "Đơn này còn thiếu dòng nào?",
                    "Trạng thái picking của đơn này?",
                    "Có nguy cơ trễ giao không?"
            );
            case DAILY_TASKS, REPORT_SUMMARY, FLOW_REPORT -> List.of(
                    "Việc nào cần ưu tiên hôm nay?",
                    "Có cảnh báo tồn thấp không?",
                    "Có đơn xuất nào đang trễ không?"
            );
            case AMBIGUOUS -> List.of(
                    "Bạn muốn hỏi về tồn kho, nhập kho hay xuất kho?",
                    "Bạn có mã SKU, PO, SO hoặc kho cụ thể không?"
            );
            default -> List.of();
        };
    }

    private List<AiActionSuggestion> suggestedActions(AiIntent intent, Map<String, Object> parameters) {
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
                    "Mở danh sách SKU dưới định mức để kiểm tra trước khi tạo PO.",
                    false,
                    "WAREHOUSE_MANAGER",
                    sanitizedParameters(parameters)
            ));
        }
        return actions;
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
}
