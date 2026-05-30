package com.ai_service.intent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiIntentCatalog {

    private static final Map<AiIntent, IntentDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        define(AiIntent.LOW_STOCK, "inventory", "StockTool.getLowStock",
                List.of("products", "stock_levels", "warehouses"),
                List.of(), List.of("warehouseCode", "limit"),
                List.of("Sản phẩm nào sắp hết hàng?", "SKU nào dưới định mức tồn kho?"),
                "Không có SKU dưới định mức theo dữ liệu hiện tại.");
        define(AiIntent.WAREHOUSE_STOCK_SUMMARY, "inventory", "StockTool.getWarehouseStockSummary",
                List.of("warehouses", "stock_levels", "products"),
                List.of(), List.of("warehouseCode", "category", "limit"),
                List.of("Kho nào còn nhiều hàng nhất?", "Tổng tồn theo từng kho."),
                "Chưa có dữ liệu tồn kho phù hợp.");
        define(AiIntent.INBOUND_TODAY, "inbound", "InboundTool.getInboundToday",
                List.of("inbound_receipts", "inbound_receipt_items", "purchase_orders", "suppliers"),
                List.of(), List.of("dateRange"),
                List.of("Hôm nay có bao nhiêu đơn nhập?", "Hàng nào nhập kho hôm nay?"),
                "Hôm nay chưa có phiếu nhập kho nào.");
        define(AiIntent.STOCK_BY_PRODUCT, "inventory", "StockTool.getStockByProduct",
                List.of("products", "stock_levels", "warehouses", "locations"),
                List.of("sku|product"), List.of("warehouseCode", "location", "lot"),
                List.of("Tồn kho hiện tại của sản phẩm X là bao nhiêu?", "SKU 00018 ở WH-002 còn bao nhiêu?"),
                "Cần tên sản phẩm hoặc SKU để tra tồn kho.");
        define(AiIntent.STOCK_BY_LOCATION, "inventory", "StockTool.getStockByLocation",
                List.of("locations", "stock_levels", "products", "warehouses"),
                List.of("location"), List.of("warehouseCode"),
                List.of("Sản phẩm A đang nằm ở vị trí nào?", "Vị trí A-01-02 đang chứa hàng gì?"),
                "Cần mã vị trí hoặc tên vị trí để tra hàng.");
        define(AiIntent.OUTBOUND_PRIORITY, "outbound", "OutboundTool.getPrioritySalesOrders",
                List.of("sales_orders", "sales_order_items", "customers"),
                List.of(), List.of("status", "dateRange", "limit"),
                List.of("Đơn xuất nào đang chờ xử lý?", "Đơn xuất nào cần ưu tiên?"),
                "Hiện chưa có đơn xuất ưu tiên hoặc đang chờ xử lý.");
        define(AiIntent.LATEST_INBOUND, "inbound", "InboundTool.getLatestInbound",
                List.of("inbound_receipts", "users", "purchase_orders", "suppliers"),
                List.of(), List.of("warehouseCode"),
                List.of("Nhân viên nào xử lý đơn nhập gần nhất?", "Phiếu nhập gần nhất là phiếu nào?"),
                "Chưa có phiếu nhập nào trong dữ liệu.");
        define(AiIntent.PRODUCT_WITHOUT_LOCATION, "inventory", "StockTool.getProductsWithoutLocation",
                List.of("products", "stock_levels", "putaway_tasks", "locations"),
                List.of(), List.of("warehouseCode", "limit"),
                List.of("Có sản phẩm nào chưa được gán vị trí không?", "SKU nào chưa có location?"),
                "Chưa ghi nhận sản phẩm nào thiếu vị trí.");
        define(AiIntent.MONTH_OVER_MONTH_FLOW, "report", "ReportTool.getMonthOverMonthFlow",
                List.of("inbound_receipts", "inbound_receipt_items", "sales_orders", "sales_order_items"),
                List.of(), List.of("dateRange"),
                List.of("Thống kê nhập xuất theo tháng.", "So sánh nhập xuất tháng này với tháng trước."),
                "Chưa đủ dữ liệu nhập/xuất để so sánh.");
        define(AiIntent.INBOUND_REPORT, "report", "ReportTool.getInboundReport",
                List.of("inbound_receipts", "inbound_receipt_items", "purchase_orders", "suppliers"),
                List.of(), List.of("dateRange", "warehouseCode"),
                List.of("Thống kê nhập kho theo ngày.", "Báo cáo nhập kho hôm nay."),
                "Chưa có dữ liệu nhập kho phù hợp.");
        define(AiIntent.OUTBOUND_REPORT, "report", "ReportTool.getOutboundReport",
                List.of("sales_orders", "sales_order_items", "picking_items"),
                List.of(), List.of("dateRange", "warehouseCode"),
                List.of("Thống kê xuất kho theo ngày.", "Báo cáo xuất kho tháng này."),
                "Chưa có dữ liệu xuất kho phù hợp.");
        define(AiIntent.REORDER_SUGGESTION, "inventory", "StockTool.getReorderSuggestions",
                List.of("products", "stock_levels", "po_items", "purchase_orders"),
                List.of(), List.of("warehouseCode", "days", "limit"),
                List.of("Gợi ý nhập thêm hàng dựa trên tồn kho thấp.", "SKU nào cần đặt bổ sung?"),
                "Hiện chưa có SKU cần gợi ý nhập bổ sung.");
    }

    private AiIntentCatalog() {
    }

    public static IntentDefinition get(AiIntent intent) {
        if (intent == null) {
            return fallback(AiIntent.UNSUPPORTED);
        }
        return DEFINITIONS.getOrDefault(intent, fallback(intent));
    }

    public static List<String> dataSources(AiIntent intent) {
        return get(intent).dataSources();
    }

    public static String routerCatalogText() {
        StringBuilder builder = new StringBuilder();
        for (AiIntent intent : AiIntent.values()) {
            IntentDefinition definition = get(intent);
            builder.append("- ")
                    .append(intent.name())
                    .append(" [").append(definition.domain()).append("]: ")
                    .append(String.join(" | ", definition.examples()))
                    .append(". Required params: ")
                    .append(definition.requiredParams().isEmpty() ? "none" : String.join(", ", definition.requiredParams()))
                    .append(".\n");
        }
        return builder.toString();
    }

    private static void define(AiIntent intent, String domain, String toolName, List<String> dataSources,
            List<String> requiredParams, List<String> optionalParams, List<String> examples, String emptyFallback) {
        DEFINITIONS.put(intent, new IntentDefinition(intent, domain, toolName, dataSources,
                requiredParams, optionalParams, examples, emptyFallback));
    }

    private static IntentDefinition fallback(AiIntent intent) {
        return new IntentDefinition(intent, "general", intent.name(), List.of(),
                List.of(), List.of(), List.of(intent.name().toLowerCase().replace('_', ' ')),
                "Tôi chưa tìm thấy dữ liệu phù hợp với câu hỏi này.");
    }

    public record IntentDefinition(
            AiIntent intent,
            String domain,
            String toolName,
            List<String> dataSources,
            List<String> requiredParams,
            List<String> optionalParams,
            List<String> examples,
            String emptyFallback
    ) {
        public List<String> missingParams(Map<String, Object> parameters) {
            if (requiredParams == null || requiredParams.isEmpty()) {
                return List.of();
            }
            Map<String, Object> safe = parameters == null ? Map.of() : parameters;
            return requiredParams.stream()
                    .filter(group -> Arrays.stream(group.split("\\|"))
                            .noneMatch(name -> hasValue(safe.get(name))))
                    .toList();
        }

        private static boolean hasValue(Object value) {
            return value != null && !String.valueOf(value).isBlank();
        }
    }
}
