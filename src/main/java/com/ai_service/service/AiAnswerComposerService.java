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
                case LOCATION_SEARCH -> "Tôi chưa tìm thấy vị trí kho phù hợp với điều kiện này.";
                case PENDING_PUTAWAY -> "Hiện chưa có task putaway nào đang chờ xử lý.";
                case PURCHASE_ORDER_STATUS -> "Tôi chưa tìm thấy đơn nhập phù hợp.";
                case OUTBOUND_PRIORITY -> "Hiện chưa có đơn xuất nào trong nhóm cần ưu tiên theo dữ liệu hiện tại.";
                case PICKING_STATUS -> "Hiện chưa có dòng picking nào đang mở.";
                case CYCLE_COUNT_VARIANCE -> "Hiện chưa ghi nhận dòng kiểm kê đang lệch tồn hoặc đang chờ đếm.";
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
        return null;
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
