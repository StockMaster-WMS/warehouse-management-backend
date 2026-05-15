package com.ai_service.service;

import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiToolExecutorService {

    private static final int DEFAULT_LIMIT = 50;

    private final JdbcTemplate jdbcTemplate;

    // Chọn tool tương ứng với intent và thực thi truy vấn.
    public AiToolResult execute(AiIntentResult route) {
        AiIntent intent = route == null || route.getIntent() == null ? AiIntent.UNSUPPORTED : route.getIntent();
        Map<String, Object> params = route == null ? Map.of() : route.safeParameters();

        return switch (intent) {
            case WAREHOUSE_COUNT -> AiToolResult.data("WarehouseTool.countWarehouses", countWarehouses());
            case WAREHOUSE_LIST -> AiToolResult.data("WarehouseTool.listWarehouses", listWarehouses());
            case WAREHOUSE_DETAIL -> AiToolResult.data("WarehouseTool.getWarehouseDetail", getWarehouseDetail(params));
            case LOCATION_SEARCH -> AiToolResult.data("LocationTool.searchLocations", searchLocations(params));
            case STOCK_BY_PRODUCT -> getStockByProductResult(params);
            case LOW_STOCK -> AiToolResult.data("StockTool.getLowStock", getLowStock());
            case NEAR_EXPIRY -> AiToolResult.data("StockTool.getNearExpiry", getNearExpiry(params));
            case PENDING_PUTAWAY -> AiToolResult.data("InboundTool.getPendingPutaway", getPendingPutaway());
            case PURCHASE_ORDER_STATUS -> AiToolResult.data("InboundTool.getPurchaseOrderStatus", getPurchaseOrders(params));
            case OUTBOUND_PRIORITY -> AiToolResult.data("OutboundTool.getPrioritySalesOrders", getPrioritySalesOrders());
            case PICKING_STATUS -> AiToolResult.data("OutboundTool.getPickingStatus", getPickingStatus());
            case CYCLE_COUNT_VARIANCE -> AiToolResult.data("CycleCountTool.getVariance", getCycleCountVariance());
            case REPORT_SUMMARY -> AiToolResult.data("ReportTool.getOperationalSummary", getOperationalSummary());
            case GENERAL_GUIDE -> AiToolResult.message("GeneralGuide", """
                    Tôi có thể hỗ trợ tra cứu kho, vị trí, tồn kho, hàng tồn thấp, hàng sắp hết hạn, putaway, đơn nhập, đơn xuất, picking, kiểm kê và tổng quan vận hành.
                    Với yêu cầu cần số liệu, hãy nêu rõ mã SKU/tên sản phẩm, kho hoặc khoảng thời gian nếu có.
                    """.trim());
            case AMBIGUOUS -> AiToolResult.message("Clarification", "Bạn vui lòng nói rõ thêm mã kho, SKU, đơn hàng hoặc khoảng thời gian cần kiểm tra.");
            case UNSUPPORTED -> AiToolResult.message("Unsupported", "Tôi hiện chỉ hỗ trợ các câu hỏi liên quan đến vận hành kho StockMaster-WMS.");
        };
    }

    // Ước lượng số dòng dữ liệu trả về để ghi audit.
    public int estimateRows(AiToolResult result) {
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

    // Đếm tổng số kho theo trạng thái hoạt động.
    private Map<String, Object> countWarehouses() {
        return jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE is_active = TRUE) AS active,
                    COUNT(*) FILTER (WHERE is_active = FALSE) AS inactive
                FROM warehouses
                """);
    }

    // Lấy danh sách kho hiện có.
    private List<Map<String, Object>> listWarehouses() {
        return jdbcTemplate.queryForList("""
                SELECT code, name, address, manager_name, timezone, is_active, created_at
                FROM warehouses
                ORDER BY is_active DESC, name ASC
                LIMIT 50
                """);
    }

    // Tìm chi tiết kho theo mã, tên hoặc địa chỉ.
    private List<Map<String, Object>> getWarehouseDetail(Map<String, Object> params) {
        String keyword = firstText(params, "warehouseCode", "warehouse", "code", "query");
        if (!StringUtils.hasText(keyword)) {
            return listWarehouses();
        }
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT code, name, address, manager_name, timezone, is_active, created_at, updated_at
                FROM warehouses
                WHERE LOWER(code) = LOWER(?)
                   OR LOWER(name) LIKE ?
                   OR LOWER(address) LIKE ?
                ORDER BY is_active DESC, name ASC
                LIMIT 10
                """, keyword, like, like);
    }

    // Tìm vị trí kho theo zone, kho hoặc mã vị trí.
    private List<Map<String, Object>> searchLocations(Map<String, Object> params) {
        String zone = text(params.get("zone"));
        String warehouse = firstText(params, "warehouseCode", "warehouse");
        String keyword = firstText(params, "location", "code", "query");

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    l.code,
                    l.zone,
                    l.aisle,
                    l.rack,
                    l.level,
                    l.bin,
                    l.location_type,
                    l.status,
                    l.is_active,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name
                FROM locations l
                JOIN warehouses w ON w.id = l.warehouse_id
                WHERE 1 = 1
                """);

        if (StringUtils.hasText(zone)) {
            sql.append(" AND LOWER(l.zone) = LOWER(?)");
            args.add(zone);
        }
        if (StringUtils.hasText(warehouse)) {
            sql.append(" AND (LOWER(w.code) = LOWER(?) OR LOWER(w.name) LIKE ?)");
            args.add(warehouse);
            args.add(like(warehouse));
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("""
                     AND (
                        LOWER(l.code) LIKE ?
                        OR LOWER(l.location_type) LIKE ?
                        OR LOWER(l.status) LIKE ?
                     )
                    """);
            String like = like(keyword);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY w.code ASC, l.zone ASC, l.pick_sequence ASC NULLS LAST, l.code ASC LIMIT ")
                .append(DEFAULT_LIMIT);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    // Resolve sản phẩm/kho trước khi tra tồn kho để phân biệt sai kho và không có tồn.
    private AiToolResult getStockByProductResult(Map<String, Object> params) {
        Map<String, Object> resolvedParams = new LinkedHashMap<>(params);
        String query = firstText(resolvedParams, "query", "product");

        ResolvedWarehouse warehouse = resolveWarehouse(resolvedParams);
        if (warehouse != null) {
            resolvedParams.put("warehouseCode", warehouse.code());
            resolvedParams.put("warehouse", warehouse.name());
        } else if (hasWarehouseHint(resolvedParams)) {
            return AiToolResult.message("StockTool.getStockByProduct",
                    "Tôi chưa tìm thấy kho phù hợp với thông tin bạn nêu. Bạn vui lòng kiểm tra lại mã hoặc tên kho.");
        }

        if (!StringUtils.hasText(text(resolvedParams.get("sku")))) {
            String resolvedSku = resolveProductSku(query);
            if (StringUtils.hasText(resolvedSku)) {
                resolvedParams.put("sku", resolvedSku);
            }
        }

        List<Map<String, Object>> rows = getStockByProduct(resolvedParams);
        if (rows.isEmpty() && warehouse != null) {
            return AiToolResult.message("StockTool.getStockByProduct",
                    "Kho " + warehouse.code() + " có tồn tại, nhưng tôi chưa tìm thấy tồn kho phù hợp với sản phẩm hoặc SKU bạn hỏi trong kho này.");
        }
        return AiToolResult.data("StockTool.getStockByProduct", rows);
    }

    // Tra cứu tồn kho theo SKU hoặc tên sản phẩm.
    private List<Map<String, Object>> getStockByProduct(Map<String, Object> params) {
        String sku = text(params.get("sku"));
        String product = firstText(params, "product", "query");
        String warehouse = firstText(params, "warehouseCode", "warehouse");

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(p.sku, sl.product_id::text) AS sku,
                    COALESCE(p.name, 'Sản phẩm ' || sl.product_id::text) AS product_name,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name,
                    l.code AS location_code,
                    sl.lot_number,
                    sl.expiry_date,
                    sl.qty_on_hand,
                    sl.qty_reserved,
                    sl.qty_available,
                    sl.updated_at
                FROM stock_levels sl
                LEFT JOIN products p ON p.id = sl.product_id
                JOIN warehouses w ON w.id = sl.warehouse_id
                JOIN locations l ON l.id = sl.location_id
                WHERE 1 = 1
                """);

        if (StringUtils.hasText(sku)) {
            sql.append(" AND LOWER(p.sku) = LOWER(?)");
            args.add(sku);
        } else if (StringUtils.hasText(product)) {
            sql.append("""
                     AND (
                        LOWER(p.sku) LIKE ?
                        OR LOWER(p.name) LIKE ?
                        OR LOWER(sl.product_id::text) LIKE ?
                     )
                    """);
            String like = like(cleanProductKeyword(product));
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (StringUtils.hasText(warehouse)) {
            sql.append(" AND (LOWER(w.code) = LOWER(?) OR LOWER(w.name) LIKE ?)");
            args.add(warehouse);
            args.add(like(warehouse));
        }

        sql.append(" ORDER BY product_name ASC, w.name ASC, l.code ASC LIMIT ").append(DEFAULT_LIMIT);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    // Lấy danh sách SKU đang dưới mức tồn tối thiểu.
    private List<Map<String, Object>> getLowStock() {
        return jdbcTemplate.queryForList("""
                SELECT
                    p.sku,
                    p.name AS product_name,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available,
                    COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(sl.qty_reserved), 0) AS qty_reserved,
                    p.min_stock_qty,
                    COUNT(DISTINCT sl.warehouse_id) AS warehouse_count
                FROM products p
                JOIN stock_levels sl ON sl.product_id = p.id
                WHERE p.status = 'ACTIVE'
                GROUP BY p.id, p.sku, p.name, p.min_stock_qty
                HAVING COALESCE(SUM(sl.qty_available), 0) < COALESCE(p.min_stock_qty, 0)
                ORDER BY qty_available ASC, p.name ASC
                LIMIT 50
                """);
    }

    // Lấy danh sách lô hàng sắp hết hạn trong số ngày yêu cầu.
    private List<Map<String, Object>> getNearExpiry(Map<String, Object> params) {
        int days = intValue(params.get("days"), 30);
        LocalDate threshold = LocalDate.now().plusDays(Math.max(days, 0));
        return jdbcTemplate.queryForList("""
                SELECT
                    COALESCE(p.sku, sl.product_id::text) AS sku,
                    COALESCE(p.name, 'Sản phẩm ' || sl.product_id::text) AS product_name,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name,
                    l.code AS location_code,
                    sl.lot_number,
                    sl.expiry_date,
                    (sl.expiry_date - CURRENT_DATE) AS days_left,
                    sl.qty_on_hand,
                    sl.qty_reserved,
                    sl.qty_available
                FROM stock_levels sl
                LEFT JOIN products p ON p.id = sl.product_id
                JOIN warehouses w ON w.id = sl.warehouse_id
                JOIN locations l ON l.id = sl.location_id
                WHERE sl.expiry_date IS NOT NULL
                  AND sl.expiry_date <= ?
                  AND sl.qty_on_hand > 0
                ORDER BY sl.expiry_date ASC, product_name ASC
                LIMIT 50
                """, threshold);
    }

    // Lấy các task putaway đang chờ xử lý.
    private List<Map<String, Object>> getPendingPutaway() {
        return jdbcTemplate.queryForList("""
                SELECT
                    pt.id,
                    pt.status,
                    pt.qty_to_putaway,
                    COALESCE(p.sku, pt.product_id::text) AS sku,
                    COALESCE(p.name, 'Sản phẩm ' || pt.product_id::text) AS product_name,
                    suggested.code AS suggested_location,
                    actual.code AS actual_location,
                    pt.completed_at
                FROM putaway_tasks pt
                LEFT JOIN products p ON p.id = pt.product_id
                LEFT JOIN locations suggested ON suggested.id = pt.suggested_location_id
                LEFT JOIN locations actual ON actual.id = pt.actual_location_id
                WHERE pt.status IN ('PENDING', 'IN_PROGRESS', 'ASSIGNED')
                ORDER BY pt.status ASC, pt.id ASC
                LIMIT 50
                """);
    }

    // Tra cứu đơn nhập theo mã hoặc trạng thái chưa hoàn thành.
    private List<Map<String, Object>> getPurchaseOrders(Map<String, Object> params) {
        String code = firstText(params, "code", "query");
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    po.po_number,
                    po.status,
                    po.order_date,
                    po.expected_date,
                    po.total_amount,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name
                FROM purchase_orders po
                LEFT JOIN warehouses w ON w.id = po.warehouse_id
                WHERE 1 = 1
                """);
        if (StringUtils.hasText(code)) {
            sql.append(" AND LOWER(po.po_number) LIKE ?");
            args.add(like(code));
        } else {
            sql.append(" AND po.status <> 'COMPLETED'");
        }
        sql.append(" ORDER BY po.expected_date ASC NULLS LAST, po.order_date DESC LIMIT ").append(DEFAULT_LIMIT);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    // Lấy các đơn xuất đang cần ưu tiên xử lý.
    private List<Map<String, Object>> getPrioritySalesOrders() {
        return jdbcTemplate.queryForList("""
                SELECT
                    so.so_number,
                    so.customer_name,
                    so.priority,
                    so.status,
                    so.created_at,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name
                FROM sales_orders so
                LEFT JOIN warehouses w ON w.id = so.warehouse_id
                WHERE so.status IN ('PENDING', 'APPROVED', 'ALLOCATED', 'PICKING')
                ORDER BY so.priority ASC, so.created_at ASC
                LIMIT 50
                """);
    }

    // Lấy tình trạng các dòng picking chưa hoàn tất.
    private List<Map<String, Object>> getPickingStatus() {
        return jdbcTemplate.queryForList("""
                SELECT
                    so.so_number,
                    so.customer_name,
                    pi.status,
                    pi.qty_to_pick,
                    pi.qty_picked,
                    pi.pick_sequence,
                    pi.lot_number,
                    COALESCE(p.sku, pi.product_id::text) AS sku,
                    COALESCE(p.name, 'Sản phẩm ' || pi.product_id::text) AS product_name,
                    l.code AS location_code
                FROM picking_items pi
                LEFT JOIN sales_order_items soi ON soi.id = pi.so_item_id
                LEFT JOIN sales_orders so ON so.id = soi.sales_order_id
                LEFT JOIN products p ON p.id = pi.product_id
                LEFT JOIN locations l ON l.id = pi.location_id
                WHERE pi.status <> 'COMPLETED'
                ORDER BY pi.status ASC, pi.pick_sequence ASC NULLS LAST
                LIMIT 50
                """);
    }

    // Lấy các dòng kiểm kê đang lệch hoặc chưa đếm xong.
    private List<Map<String, Object>> getCycleCountVariance() {
        return jdbcTemplate.queryForList("""
                SELECT
                    cc.id AS cycle_count_id,
                    cc.status AS cycle_count_status,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name,
                    cci.status AS item_status,
                    COALESCE(p.sku, cci.product_id::text) AS sku,
                    COALESCE(p.name, 'Sản phẩm ' || cci.product_id::text) AS product_name,
                    l.code AS location_code,
                    cci.lot_number,
                    cci.system_qty,
                    cci.counted_qty,
                    cci.discrepancy
                FROM cycle_count_items cci
                JOIN cycle_counts cc ON cc.id = cci.cycle_count_id
                LEFT JOIN warehouses w ON w.id = cc.warehouse_id
                LEFT JOIN products p ON p.id = cci.product_id
                LEFT JOIN locations l ON l.id = cci.location_id
                WHERE COALESCE(cci.discrepancy, 0) <> 0
                   OR cci.status IN ('PENDING', 'COUNTING')
                ORDER BY ABS(COALESCE(cci.discrepancy, 0)) DESC, cc.created_at DESC
                LIMIT 50
                """);
    }

    // Tổng hợp nhanh các chỉ số vận hành kho.
    private Map<String, Object> getOperationalSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("warehouses", countWarehouses());
        summary.put("stock", jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(DISTINCT product_id) AS total_skus,
                    COALESCE(SUM(qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(qty_reserved), 0) AS qty_reserved,
                    COALESCE(SUM(qty_available), 0) AS qty_available
                FROM stock_levels
                """));
        summary.put("near_expiry_30_days", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total
                FROM stock_levels
                WHERE expiry_date IS NOT NULL
                  AND expiry_date <= ?
                  AND qty_on_hand > 0
                """, LocalDate.now().plusDays(30)));
        summary.put("pending_putaway", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total
                FROM putaway_tasks
                WHERE status IN ('PENDING', 'IN_PROGRESS', 'ASSIGNED')
                """));
        summary.put("priority_outbound", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total
                FROM sales_orders
                WHERE status IN ('PENDING', 'APPROVED', 'ALLOCATED', 'PICKING')
                """));
        return summary;
    }

    // Lấy chuỗi đầu tiên có giá trị từ danh sách key.
    private String firstText(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            String value = text(params.get(key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    // Chuyển object thành text sạch hoặc null.
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.equalsIgnoreCase("null")) {
            return null;
        }
        return text;
    }

    // Chuyển object thành số nguyên với fallback.
    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    // Tạo pattern LIKE an toàn cho tìm kiếm không phân biệt hoa thường.
    private String like(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    // Loại bỏ từ khóa thừa để tìm tên sản phẩm chính xác hơn.
    private String cleanProductKeyword(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("(?i)tồn kho|ton kho|còn bao nhiêu|con bao nhieu|ở kho|o kho|sku|sản phẩm|san pham", "")
                .trim();
    }

    // Tìm SKU phù hợp nhất từ câu hỏi tự nhiên.
    private String resolveProductSku(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String normalizedQuery = normalize(query);
        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT sku, name
                FROM products
                WHERE status = 'ACTIVE'
                ORDER BY name ASC
                LIMIT 500
                """);

        String bestSku = null;
        int bestScore = 0;
        for (Map<String, Object> product : products) {
            String sku = text(product.get("sku"));
            String name = text(product.get("name"));
            int score = scoreCandidate(normalizedQuery, sku, name);
            if (score > bestScore) {
                bestScore = score;
                bestSku = sku;
            }
        }
        return bestScore >= 2 ? bestSku : null;
    }

    // Tìm kho phù hợp nhất từ mã kho, tên kho hoặc địa chỉ trong câu hỏi.
    private ResolvedWarehouse resolveWarehouse(Map<String, Object> params) {
        String explicitWarehouse = firstText(params, "warehouseCode", "warehouse");
        String query = firstText(params, "query");
        String searchText = StringUtils.hasText(explicitWarehouse) ? explicitWarehouse : query;
        if (!StringUtils.hasText(searchText)) {
            return null;
        }
        String normalizedSearch = normalize(searchText);
        List<Map<String, Object>> warehouses = jdbcTemplate.queryForList("""
                SELECT code, name, address, is_active
                FROM warehouses
                ORDER BY is_active DESC, name ASC
                LIMIT 200
                """);

        ResolvedWarehouse best = null;
        int bestScore = 0;
        for (Map<String, Object> warehouse : warehouses) {
            String code = text(warehouse.get("code"));
            String name = text(warehouse.get("name"));
            String address = text(warehouse.get("address"));
            int score = scoreCandidate(normalizedSearch, code, name, address);
            if (StringUtils.hasText(explicitWarehouse) && normalize(explicitWarehouse).equals(normalize(code))) {
                score += 10;
            }
            if (score > bestScore) {
                bestScore = score;
                best = new ResolvedWarehouse(code, name, Boolean.TRUE.equals(warehouse.get("is_active")));
            }
        }
        return bestScore >= 2 ? best : null;
    }

    // Kiểm tra câu hỏi có nhắc đến một kho cụ thể hay không.
    private boolean hasWarehouseHint(Map<String, Object> params) {
        if (StringUtils.hasText(firstText(params, "warehouseCode", "warehouse"))) {
            return true;
        }
        String normalized = normalize(firstText(params, "query"));
        return normalized.contains(" o ") || normalized.contains(" tai ") || normalized.contains(" trong kho ")
                || normalized.contains(" kho wh-");
    }

    // Chấm điểm candidate theo token xuất hiện trong câu hỏi.
    private int scoreCandidate(String normalizedQuery, String... values) {
        int score = 0;
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String normalizedValue = normalize(value);
            if (normalizedQuery.contains(normalizedValue)) {
                score += 5;
            }
            for (String token : normalizedValue.split("[^a-z0-9]+")) {
                if (isMeaningfulToken(token) && normalizedQuery.contains(token)) {
                    score++;
                }
            }
        }
        return score;
    }

    // Bỏ các từ chung để tránh match sai khi resolve entity.
    private boolean isMeaningfulToken(String token) {
        return StringUtils.hasText(token)
                && token.length() >= 2
                && !List.of("kho", "ton", "hang", "san", "pham", "con", "bao", "nhieu", "tai", "the",
                        "nao", "stock", "qty", "available", "reserved", "dc01").contains(token);
    }

    // Chuẩn hóa text về không dấu và chữ thường.
    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }

    private record ResolvedWarehouse(String code, String name, boolean active) {
    }
}
