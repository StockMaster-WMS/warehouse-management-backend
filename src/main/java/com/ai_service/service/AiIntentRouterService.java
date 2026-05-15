package com.ai_service.service;

import com.ai_service.client.OllamaClient;
import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiIntentRouterService {

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)\\s*(?:ngay|ngày|day|days)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKU_PATTERN = Pattern.compile("\\b[A-Z0-9]{2,}(?:-[A-Z0-9]{2,})+\\b");
    private static final Pattern WAREHOUSE_CODE_PATTERN = Pattern.compile("\\bWH-[A-Z0-9-]+\\b", Pattern.CASE_INSENSITIVE);

    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public AiIntentResult route(String userMessage, List<Map<String, String>> history) {
        try {
            String raw = ollamaClient.generateIntent(buildRouterPrompt(userMessage, history));
            AiIntentResult parsed = parseIntent(raw);
            if (parsed.getIntent() != AiIntent.UNSUPPORTED || looksUnsupported(userMessage)) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("AI intent routing failed, using heuristic fallback: {}", e.getMessage());
        }
        return heuristic(userMessage);
    }

    private String buildRouterPrompt(String userMessage, List<Map<String, String>> history) {
        return """
                <|im_start|>system
                Bạn là bộ định tuyến intent cho trợ lý StockMaster-WMS.
                Nhiệm vụ: đọc câu hỏi tiếng Việt và trả về đúng 1 JSON object, không markdown, không giải thích.

                JSON schema bắt buộc:
                {
                  "intent": "ONE_OF_SUPPORTED_INTENTS",
                  "parameters": {
                    "sku": null,
                    "product": null,
                    "warehouse": null,
                    "warehouseCode": null,
                    "location": null,
                    "zone": null,
                    "days": null,
                    "status": null,
                    "code": null,
                    "query": null,
                    "dateRange": null,
                    "fromDate": null,
                    "toDate": null,
                    "limit": null
                  },
                  "confidence": 0.0,
                  "reason": "short reason"
                }

                Supported intents:
                - WAREHOUSE_COUNT: hỏi tổng số/số lượng kho.
                - WAREHOUSE_LIST: hỏi danh sách/liệt kê các kho.
                - WAREHOUSE_DETAIL: hỏi chi tiết một kho cụ thể theo mã/tên.
                - LOCATION_SEARCH: hỏi vị trí, location, zone, aisle, rack, bin.
                - STOCK_BY_PRODUCT: hỏi tồn kho của SKU/sản phẩm, có thể kèm kho.
                - LOW_STOCK: hỏi hàng tồn thấp, gần hết hàng, dưới định mức.
                - NEAR_EXPIRY: hỏi hàng sắp hết hạn/hết hạn trong N ngày.
                - PENDING_PUTAWAY: hỏi task putaway/chờ cất hàng/chưa đưa vào vị trí.
                - PURCHASE_ORDER_STATUS: hỏi trạng thái đơn nhập/PO.
                - OUTBOUND_PRIORITY: hỏi đơn xuất cần ưu tiên/xử lý gấp.
                - PICKING_STATUS: hỏi picking/lấy hàng/chờ pick.
                - CYCLE_COUNT_VARIANCE: hỏi kiểm kê, lệch tồn, chênh lệch kiểm đếm.
                - REPORT_SUMMARY: hỏi tổng quan/tóm tắt dashboard/báo cáo vận hành.
                - GENERAL_GUIDE: chào hỏi hoặc hỏi trợ lý làm được gì.
                - AMBIGUOUS: thiếu thông tin quan trọng cần hỏi lại.
                - UNSUPPORTED: ngoài phạm vi hệ thống kho.

                Quy tắc:
                - Nếu người dùng hỏi "kho đó", "sản phẩm đó", "đơn đó" thì dùng history gần nhất để suy luận tham chiếu.
                - Nếu không suy luận được tham chiếu, trả AMBIGUOUS.
                - Với câu hỏi hết hạn không nêu số ngày, đặt days = 30.
                - Với câu hỏi tồn kho sản phẩm, tách sku nếu có mã SKU; nếu không có SKU thì tách tên sản phẩm vào product.
                - Với "hôm nay", "tuần này", "7 ngày qua", "tháng này", đặt dateRange tương ứng TODAY, THIS_WEEK, LAST_7_DAYS, THIS_MONTH.
                - Nếu người dùng yêu cầu tạo/sửa/xóa/duyệt/hủy dữ liệu qua chat, trả UNSUPPORTED.
                - Không sinh SQL.
                <|im_end|>
                <|im_start|>user
                History JSON: %s
                Current question: %s
                <|im_end|>
                <|im_start|>assistant
                """.formatted(toJson(history), userMessage);
    }

    private AiIntentResult parseIntent(String raw) throws Exception {
        String json = extractJson(raw);
        JsonNode node = objectMapper.readTree(json);

        AiIntent intent = parseIntentName(node.path("intent").asText("UNSUPPORTED"));
        Map<String, Object> parameters = node.has("parameters") && node.get("parameters").isObject()
                ? objectMapper.convertValue(node.get("parameters"), new TypeReference<>() {})
                : new LinkedHashMap<>();
        double confidence = node.path("confidence").asDouble(0.0);
        String reason = node.path("reason").asText(null);

        return AiIntentResult.of(intent, parameters, confidence, reason);
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{\"intent\":\"UNSUPPORTED\",\"parameters\":{},\"confidence\":0,\"reason\":\"empty model output\"}";
        }
        String cleaned = raw.trim()
                .replace("```json", "")
                .replace("```", "")
                .trim();
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(cleaned);
        return matcher.find() ? matcher.group(0) : cleaned;
    }

    private AiIntent parseIntentName(String value) {
        try {
            return AiIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return AiIntent.UNSUPPORTED;
        }
    }

    private AiIntentResult heuristic(String userMessage) {
        String normalized = normalize(userMessage);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", userMessage);

        if (containsAny(normalized, "cap nhat", "xoa ", "delete", "drop table", "update ", "insert ",
                "alter table", "truncate", "sua so luong", "doi so luong", "thanh 999",
                "tao don", "huy don", "duyet po", "duyet don", "gia vo", "bo qua system prompt")) {
            return AiIntentResult.of(AiIntent.UNSUPPORTED, params, 0.9, "heuristic unsafe mutation request");
        }

        Matcher skuMatcher = SKU_PATTERN.matcher(userMessage == null ? "" : userMessage.toUpperCase(Locale.ROOT));
        if (skuMatcher.find()) {
            params.put("sku", skuMatcher.group());
        }

        Matcher whMatcher = WAREHOUSE_CODE_PATTERN.matcher(userMessage == null ? "" : userMessage);
        if (whMatcher.find()) {
            params.put("warehouseCode", whMatcher.group().toUpperCase(Locale.ROOT));
        }

        Integer days = extractDays(userMessage);
        if (days != null) {
            params.put("days", days);
        }
        String dateRange = extractDateRange(normalized);
        if (dateRange != null) {
            params.put("dateRange", dateRange);
        }

        if (containsAny(normalized, "xin chao", "hello", "hi ", "ban lam duoc gi", "tro ly lam duoc gi")) {
            return AiIntentResult.of(AiIntent.GENERAL_GUIDE, params, 0.8, "heuristic greeting");
        }
        if (containsAny(normalized, "bao nhieu kho", "co may kho", "tong so kho", "so luong kho")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_COUNT, params, 0.85, "heuristic warehouse count");
        }
        if (containsAny(normalized, "danh sach kho", "liet ke kho", "cac kho", "nhung kho")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_LIST, params, 0.85, "heuristic warehouse list");
        }
        if (containsAny(normalized, "chi tiet kho", "thong tin kho") || params.containsKey("warehouseCode")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_DETAIL, params, 0.75, "heuristic warehouse detail");
        }
        if (containsAny(normalized, "vi tri", "location", "zone", "aisle", "rack", "bin", "khu ")) {
            params.putIfAbsent("zone", extractZone(userMessage));
            return AiIntentResult.of(AiIntent.LOCATION_SEARCH, params, 0.75, "heuristic location");
        }
        if (containsAny(normalized, "sap het han", "gan het han", "het han")) {
            params.putIfAbsent("days", days == null ? 30 : days);
            return AiIntentResult.of(AiIntent.NEAR_EXPIRY, params, 0.85, "heuristic near expiry");
        }
        if (containsAny(normalized, "ton thap", "gan het hang", "duoi dinh muc", "can bo sung", "sap het hang")) {
            return AiIntentResult.of(AiIntent.LOW_STOCK, params, 0.85, "heuristic low stock");
        }
        if (containsAny(normalized, "ton kho", "con bao nhieu", "qty", "so luong ton", "sku")) {
            return AiIntentResult.of(AiIntent.STOCK_BY_PRODUCT, params, 0.75, "heuristic stock by product");
        }
        if (containsAny(normalized, "putaway", "cat hang", "cho dua vao vi tri", "cho putaway")) {
            return AiIntentResult.of(AiIntent.PENDING_PUTAWAY, params, 0.8, "heuristic putaway");
        }
        if (containsAny(normalized, "don nhap", "purchase order", " po ", "trang thai po")) {
            return AiIntentResult.of(AiIntent.PURCHASE_ORDER_STATUS, params, 0.75, "heuristic purchase order");
        }
        if (containsAny(normalized, "don xuat", "sales order", "uu tien xu ly", "xu ly gap")) {
            return AiIntentResult.of(AiIntent.OUTBOUND_PRIORITY, params, 0.75, "heuristic outbound");
        }
        if (containsAny(normalized, "picking", "pick", "lay hang", "cho pick")) {
            return AiIntentResult.of(AiIntent.PICKING_STATUS, params, 0.75, "heuristic picking");
        }
        if (containsAny(normalized, "kiem ke", "lech ton", "chenh lech", "cycle count")) {
            return AiIntentResult.of(AiIntent.CYCLE_COUNT_VARIANCE, params, 0.75, "heuristic cycle count");
        }
        if (containsAny(normalized, "tom tat", "tong quan", "dashboard", "bao cao", "tinh hinh")) {
            return AiIntentResult.of(AiIntent.REPORT_SUMMARY, params, 0.75, "heuristic report");
        }
        return AiIntentResult.of(AiIntent.UNSUPPORTED, params, 0.4, "heuristic unsupported");
    }

    private boolean looksUnsupported(String userMessage) {
        String normalized = normalize(userMessage);
        return containsAny(normalized, "thoi tiet", "bong da", "chung khoan", "nau an", "viet code",
                "cap nhat", "xoa ", "delete", "drop table", "update ", "insert ", "alter table", "truncate",
                "tao don", "huy don", "duyet po", "duyet don", "gia vo", "bo qua system prompt");
    }

    private Integer extractDays(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = DAYS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractZone(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?i)(?:zone|khu)\\s+([A-Z0-9-]+)").matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private String extractDateRange(String normalizedText) {
        if (normalizedText == null) {
            return null;
        }
        if (normalizedText.contains("hom nay") || normalizedText.contains("today")) {
            return "TODAY";
        }
        if (normalizedText.contains("tuan nay") || normalizedText.contains("this week")) {
            return "THIS_WEEK";
        }
        if (normalizedText.contains("7 ngay qua") || normalizedText.contains("last 7 days")) {
            return "LAST_7_DAYS";
        }
        if (normalizedText.contains("thang nay") || normalizedText.contains("this month")) {
            return "THIS_MONTH";
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

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
