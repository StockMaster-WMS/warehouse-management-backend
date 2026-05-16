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

    // Định tuyến câu hỏi người dùng thành intent và tham số.
    public AiIntentResult route(String userMessage, List<Map<String, String>> history) {
        AiIntentResult deterministic = deterministic(userMessage, false);
        if (deterministic != null) {
            return deterministic;
        }

        try {
            String raw = ollamaClient.generateIntent(buildRouterPrompt(userMessage, history));
            AiIntentResult parsed = correctIntent(userMessage, parseIntent(raw));
            if (parsed.getIntent() != AiIntent.UNSUPPORTED || looksUnsupported(userMessage)) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("AI intent routing failed, using heuristic fallback: {}", e.getMessage());
        }
        return heuristic(userMessage);
    }

    // Tạo prompt để model trả về JSON intent.
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
                - STOCK_TOTAL: hỏi tổng tồn kho toàn hệ thống.
                - STOCK_LOWEST: hỏi sản phẩm có tồn kho thấp nhất.
                - WAREHOUSE_STOCK_SUMMARY: hỏi kho nào nhiều tồn nhất hoặc tồn nhiều nhóm hàng trong một kho.
                - LOW_STOCK: hỏi hàng tồn thấp, gần hết hàng, dưới định mức.
                - NEAR_EXPIRY: hỏi hàng sắp hết hạn/hết hạn trong N ngày.
                - PENDING_PUTAWAY: hỏi task putaway/chờ cất hàng/chưa đưa vào vị trí.
                - LATEST_INBOUND: hỏi đơn nhập/phiếu nhập/sản phẩm vừa nhập gần đây nhất.
                - PURCHASE_ORDER_STATUS: hỏi trạng thái đơn nhập/PO.
                - OUTBOUND_PRIORITY: hỏi đơn xuất cần ưu tiên/xử lý gấp.
                - PICKING_STATUS: hỏi picking/lấy hàng/chờ pick.
                - ACTIVE_CYCLE_COUNTS: hỏi lịch kiểm kê/cycle count đang diễn ra.
                - CYCLE_COUNT_VARIANCE: hỏi kiểm kê, lệch tồn, chênh lệch kiểm đếm.
                - RMA_PENDING: hỏi yêu cầu trả hàng/RMA đang chờ xử lý.
                - DAILY_TASKS: hỏi hôm nay cần làm gì, việc cần xử lý.
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

    // Parse JSON intent từ output thô của model.
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

    // Lấy phần JSON object từ text model trả về.
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

    // Chuyển tên intent dạng text sang enum an toàn.
    private AiIntent parseIntentName(String value) {
        try {
            return AiIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return AiIntent.UNSUPPORTED;
        }
    }

    // Dự phòng khi model lỗi hoặc không trả intent đáng tin.
    private AiIntentResult heuristic(String userMessage) {
        AiIntentResult deterministic = deterministic(userMessage, true);
        if (deterministic != null) {
            return deterministic;
        }
        Map<String, Object> params = extractCommonParams(userMessage);
        return AiIntentResult.of(AiIntent.UNSUPPORTED, params, 0.4, "heuristic unsupported");
    }

    // Sửa intent model bằng rule chắc chắn nếu có.
    private AiIntentResult correctIntent(String userMessage, AiIntentResult parsed) {
        AiIntentResult deterministic = deterministic(userMessage, false);
        if (deterministic == null) {
            return parsed;
        }
        Map<String, Object> merged = new LinkedHashMap<>(parsed.safeParameters());
        merged.putAll(deterministic.safeParameters());
        return AiIntentResult.of(deterministic.getIntent(), merged,
                Math.max(deterministic.getConfidence(), parsed.getConfidence() == null ? 0.0 : parsed.getConfidence()),
                "deterministic correction: " + deterministic.getReason());
    }

    // Bắt các intent rõ ràng bằng keyword trước khi gọi model.
    private AiIntentResult deterministic(String userMessage, boolean includeFallback) {
        String normalized = normalize(userMessage);
        Map<String, Object> params = extractCommonParams(userMessage);

        if (containsAny(normalized, "thoi tiet", "bong da", "chung khoan", "nau an", "bai tho", "viet tho",
                "cap nhat", "xoa ", "delete", "drop table", "update ", "insert ",
                "alter table", "truncate", "sua so luong", "doi so luong", "thanh 999",
                "tao don", "huy don", "duyet po", "duyet don", "gia vo", "bo qua system prompt",
                "ignore system prompt", "sql update", "sql delete")) {
            return AiIntentResult.of(AiIntent.UNSUPPORTED, params, 0.95, "deterministic unsupported");
        }

        if (containsAny(normalized, "kho do", "san pham do", "san pham nay", "san pham kia",
                "don do", "don nay", "don kia", "cai nay", "lo nay", "hang nay", "mat hang do",
                "con nay")) {
            return AiIntentResult.of(AiIntent.AMBIGUOUS, params, 0.9, "deterministic ambiguous reference");
        }

        if (containsAny(normalized, "ton kho thap nhat", "ton thap nhat", "hang ton thap nhat",
                "hang ton thap", "san pham nao dang co ton kho thap nhat")) {
            return AiIntentResult.of(AiIntent.STOCK_LOWEST, params, 0.9, "deterministic stock lowest");
        }

        if (containsAny(normalized, "hang hot", "hot hom nay")) {
            return AiIntentResult.of(AiIntent.SALES_TOP, params, 0.85, "deterministic colloquial hot items");
        }

        if (containsAny(normalized, "xin chao", "hello", "hi ", "ban co the giup gi", "ban lam duoc gi",
                "tro ly kho", "tro ly lam duoc gi", "putaway la gi", "la gi", "duoc tinh the nao",
                "nen xu ly ra sao", "huong dan", "giai thich", "cach ", "lam sao")) {
            return AiIntentResult.of(AiIntent.GENERAL_GUIDE, params, 0.9, "deterministic guide");
        }

        if (containsAny(normalized, "bao nhieu kho", "co may kho", "tong so kho", "so luong kho",
                "how many warehouses", "tong warehouse")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_COUNT, params, 0.9, "deterministic warehouse count");
        }

        if (containsAny(normalized, "hom nay co gi can lam", "hom nay kho co viec gi", "hom nay kho co viec",
                "can lam hom nay", "viec can lam", "can xu ly hom nay", "don hom nay co gi gap")) {
            return AiIntentResult.of(AiIntent.DAILY_TASKS, params, 0.9, "deterministic daily tasks");
        }

        if (containsAny(normalized, "tom tat", "tong quan", "dashboard", "operation summary", "tinh hinh")) {
            return AiIntentResult.of(AiIntent.REPORT_SUMMARY, params, 0.9, "deterministic report");
        }

        if (containsAny(normalized, "bao cao nhap - xuat", "bao cao nhap xuat", "nhap - xuat kho", "nhap xuat kho")) {
            return AiIntentResult.of(AiIntent.FLOW_REPORT, params, 0.9, "deterministic flow report");
        }

        if (containsAny(normalized, "ban chay", "top 5 san pham ban")) {
            return AiIntentResult.of(AiIntent.SALES_TOP, params, 0.9, "deterministic sales top");
        }

        if (containsAny(normalized, "bao cao ton kho")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_STOCK_SUMMARY, params, 0.9, "deterministic inventory report");
        }

        if (containsAny(normalized, "kho nao dang ban nhat")) {
            return AiIntentResult.of(AiIntent.PUTAWAY_BY_WAREHOUSE, params, 0.9, "deterministic busiest warehouse");
        }

        if (containsAny(normalized, "hoat dong nhieu nhat", "kho nao dang hoat dong nhieu",
                "kho nao con hang nhieu")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_STOCK_SUMMARY, params, 0.9, "deterministic busiest warehouse");
        }

        if (containsAny(normalized, "danh sach kho", "liet ke kho", "cac kho", "nhung kho", "kho dang active",
                "list all active warehouses", "warehouse code")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_LIST, params, 0.9, "deterministic warehouse list");
        }

        if (containsAny(normalized, "putaway", "cat hang", "cho cat vao vi tri", "cho dua vao vi tri", "cho putaway")) {
            if (containsAny(normalized, "kho nao")) {
                return AiIntentResult.of(AiIntent.PUTAWAY_BY_WAREHOUSE, params, 0.9, "deterministic putaway by warehouse");
            }
            return AiIntentResult.of(AiIntent.PENDING_PUTAWAY, params, 0.9, "deterministic putaway");
        }

        if (containsAny(normalized, "san pham nang", "hang nang", "heavy")) {
            return AiIntentResult.of(AiIntent.BEST_HEAVY_LOCATION, params, 0.9, "deterministic heavy location");
        }

        if (containsAny(normalized, "bao nhieu vi tri", "so luong vi tri", "vi tri luu tru")) {
            return AiIntentResult.of(AiIntent.LOCATION_COUNT, params, 0.9, "deterministic location count");
        }

        if (containsAny(normalized, "vi tri", "location", "locations", "zone", "aisle", "rack", "bin", "khu ", "available", "maintenance")) {
            params.putIfAbsent("zone", extractZone(userMessage));
            return AiIntentResult.of(AiIntent.LOCATION_SEARCH, params, 0.9, "deterministic location");
        }

        if (containsAny(normalized, "tong ton kho", "tong so luong ton", "tong hang ton", "tong ton")) {
            return AiIntentResult.of(AiIntent.STOCK_TOTAL, params, 0.9, "deterministic stock total");
        }

        if (containsAny(normalized, "ton kho cao nhat", "hang ton cao nhat", "san pham nao co ton kho cao nhat")) {
            return AiIntentResult.of(AiIntent.STOCK_HIGHEST, params, 0.9, "deterministic stock highest");
        }

        if (containsAny(normalized, "barcode")) {
            return AiIntentResult.of(AiIntent.PRODUCT_BY_BARCODE, params, 0.9, "deterministic product by barcode");
        }

        if (containsAny(normalized, "lot number", "theo doi lot", "quan ly lot")) {
            return AiIntentResult.of(AiIntent.LOT_TRACKED_COUNT, params, 0.9, "deterministic lot tracked count");
        }

        if (containsAny(normalized, "duoi 10", "duoi 5", "duoi 20", "duoi 50")) {
            return AiIntentResult.of(AiIntent.STOCK_BELOW_THRESHOLD, params, 0.9, "deterministic stock below threshold");
        }

        if (containsAny(normalized, "kho nao") && containsAny(normalized, "nhieu hang ton", "nhieu ton kho", "ton kho nhieu nhat")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_STOCK_SUMMARY, params, 0.9, "deterministic warehouse stock summary");
        }

        if (containsAny(normalized, "iphone va laptop", "iphone/laptop", "iphone", "laptop")
                && containsAny(normalized, "kho hcm", "tp.hcm", "tp hcm", "wh-hcm", "ha noi", "wh-hn")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_STOCK_SUMMARY, params, 0.9, "deterministic warehouse product groups");
        }

        if (containsAny(normalized, "iphone", "laptop", "dell", "xps")
                && containsAny(normalized, "kho hcm", "tp.hcm", "tp hcm", "wh-hcm", "ha noi", "wh-hn", "da nang")) {
            return AiIntentResult.of(AiIntent.STOCK_BY_PRODUCT, params, 0.9, "deterministic stock by product in warehouse");
        }

        if (containsAny(normalized, "kho hcm", "tp.hcm", "tp hcm", "wh-hcm", "ha noi", "wh-hn")
                && containsAny(normalized, "con bao nhieu", "con hang", "con khong")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_STOCK_SUMMARY, params, 0.9, "deterministic warehouse stock summary");
        }

        if (containsAny(normalized, "chi tiet kho", "thong tin kho", "kho ha noi", "kho hcm", "kho tp.hcm",
                "kho tp hcm", "kho binh duong", "ma kho", "quan ly kho", "nguoi quan ly")
                || params.containsKey("warehouseCode")) {
            return AiIntentResult.of(AiIntent.WAREHOUSE_DETAIL, params, 0.9, "deterministic warehouse detail");
        }

        if (containsAny(normalized, "ton thap", "gan het hang", "duoi dinh muc", "duoi muc an toan",
                "can bo sung", "sap het hang", "low stock")) {
            return AiIntentResult.of(AiIntent.LOW_STOCK, params, 0.9, "deterministic low stock");
        }

        if (containsAny(normalized, "sap het han", "gan het han", "het han ", "het han?", "expiry", "expired", "fefo")) {
            params.putIfAbsent("days", params.get("days") == null ? 30 : params.get("days"));
            return AiIntentResult.of(AiIntent.NEAR_EXPIRY, params, 0.9, "deterministic near expiry");
        }

        if (containsAny(normalized, "rma", "tra hang", "yeu cau tra")) {
            return AiIntentResult.of(AiIntent.RMA_PENDING, params, 0.9, "deterministic rma");
        }

        if (containsAny(normalized, "vua duoc nhap kho hom nay", "nhap kho hom nay", "lo hang hom nay")) {
            return AiIntentResult.of(AiIntent.INBOUND_TODAY, params, 0.9, "deterministic inbound today");
        }

        if (containsAny(normalized, "vua nhap", "nhap gan day", "gan day nhat", "don nhap kho gan nhat",
                "phieu nhap gan nhat", "lo hang moi nhat")) {
            return AiIntentResult.of(AiIntent.LATEST_INBOUND, params, 0.9, "deterministic latest inbound");
        }

        if (containsAny(normalized, "picking nhieu nhat", "duoc picking nhieu nhat")) {
            return AiIntentResult.of(AiIntent.PICKING_TOP, params, 0.9, "deterministic picking top");
        }

        if (containsAny(normalized, "pick xong chua") && !containsAny(normalized, " so ", "so-", "don so")) {
            return AiIntentResult.of(AiIntent.AMBIGUOUS, params, 0.85, "deterministic ambiguous picking reference");
        }

        if (containsAny(normalized, "picking", "pick", "lay hang", "cho pick", "task lay hang", "pick xong")) {
            return AiIntentResult.of(AiIntent.PICKING_STATUS, params, 0.9, "deterministic picking");
        }

        if (containsAny(normalized, "cho packing", "dang cho packing", "packing")) {
            return AiIntentResult.of(AiIntent.PACKING_STATUS, params, 0.9, "deterministic packing");
        }

        if (containsAny(normalized, "don xuat", "sales order", "priority outbound", "uu tien xu ly",
                "uu tien cao", "xu ly gap", "can xu ly gap", "so nao nen pick truoc")) {
            return AiIntentResult.of(AiIntent.OUTBOUND_PRIORITY, params, 0.9, "deterministic outbound");
        }

        if (containsAny(normalized, "ton kho", "ton bao nhieu", "con bao nhieu", "con khong",
                "qty", "so luong ton", "sku", "con ton", "con hang", "available", "reserved",
                "check stock", "stock ")) {
            return AiIntentResult.of(AiIntent.STOCK_BY_PRODUCT, params, 0.85, "deterministic stock by product");
        }

        if (containsAny(normalized, "lich kiem ke", "dang dien ra", "dang thuc hien", "dang kiem ke")) {
            return AiIntentResult.of(AiIntent.ACTIVE_CYCLE_COUNTS, params, 0.9, "deterministic active cycle count");
        }

        if (containsAny(normalized, "kiem ke", "lech ton", "chenh lech", "cycle count", "count lech")) {
            return AiIntentResult.of(AiIntent.CYCLE_COUNT_VARIANCE, params, 0.9, "deterministic cycle count");
        }

        if (containsAny(normalized, "tom tat", "tong quan", "dashboard", "bao cao", "tinh hinh")) {
            return AiIntentResult.of(AiIntent.REPORT_SUMMARY, params, 0.9, "deterministic report");
        }

        if (containsAny(normalized, "ton kho", "stock") && containsAny(normalized, "don xuat", "sales order", "outbound", "uu tien")) {
            return AiIntentResult.of(AiIntent.AMBIGUOUS, params, 0.9, "deterministic multi-intent question");
        }

        if (containsAny(normalized, "don nhap", "purchase order", " po ", "trang thai po", "po-", "po dang")) {
            if (containsAny(normalized, "cho nhan", "chua nhan", "dang cho nhan", "nhan hang chua")) {
                return AiIntentResult.of(AiIntent.PENDING_PO_RECEIPT, params, 0.9, "deterministic pending po receipt");
            }
            return AiIntentResult.of(AiIntent.PURCHASE_ORDER_STATUS, params, 0.9, "deterministic purchase order");
        }

        if (includeFallback) {
            return AiIntentResult.of(AiIntent.UNSUPPORTED, params, 0.4, "heuristic unsupported");
        }
        return null;
    }

    // Trích các tham số chung như query, SKU, mã kho, số ngày.
    private Map<String, Object> extractCommonParams(String userMessage) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", userMessage);

        Matcher whMatcher = WAREHOUSE_CODE_PATTERN.matcher(userMessage == null ? "" : userMessage);
        if (whMatcher.find()) {
            params.put("warehouseCode", whMatcher.group().toUpperCase(Locale.ROOT));
        }

        Matcher skuMatcher = SKU_PATTERN.matcher(userMessage == null ? "" : userMessage.toUpperCase(Locale.ROOT));
        if (skuMatcher.find()) {
            String code = skuMatcher.group();
            if (!code.startsWith("WH-") && !code.startsWith("PO-") && !code.startsWith("SO-")) {
                params.put("sku", code);
            }
        }

        Integer days = extractDays(userMessage);
        if (days != null) {
            params.put("days", days);
        }
        String dateRange = extractDateRange(normalize(userMessage));
        if (dateRange != null) {
            params.put("dateRange", dateRange);
        }
        return params;
    }

    // Kiểm tra câu hỏi có nằm ngoài phạm vi hoặc nguy hiểm không.
    private boolean looksUnsupported(String userMessage) {
        String normalized = normalize(userMessage);
        return containsAny(normalized, "thoi tiet", "bong da", "chung khoan", "nau an", "viet code",
                "cap nhat", "xoa ", "delete", "drop table", "update ", "insert ", "alter table", "truncate",
                "tao don", "huy don", "duyet po", "duyet don", "gia vo", "bo qua system prompt");
    }

    // Trích số ngày trong câu hỏi như "7 ngày" hoặc "30 days".
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

    // Trích mã zone/khu từ câu hỏi.
    private String extractZone(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?i)(?:zone|khu)\\s+([A-Z0-9-]+)").matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    // Chuyển cụm thời gian tự nhiên sang mã dateRange.
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

    // Kiểm tra text có chứa một trong các keyword không.
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

    // Chuẩn hóa text để so khớp không dấu và không phân biệt hoa thường.
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        return normalized
                .replaceAll("\\biphon\\b", "iphone")
                .replaceAll("\\blap\\s+top\\b", "laptop")
                .replaceAll("\\bbn\\b", "bao nhieu")
                .replaceAll("\\bko\\b", "khong")
                .replaceAll("\\bk\\b", "khong")
                .replaceAll("\\br\\b", "roi");
    }

    // Chuyển object sang JSON để đưa vào prompt.
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
