package com.ai_service.service;

import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiToolExecutorService {

    private static final int DEFAULT_LIMIT = 50;
    private static final String ADMIN = "ADMIN";
    private static final String WAREHOUSE_MANAGER = "WAREHOUSE_MANAGER";
    private static final String WAREHOUSE_STAFF = "WAREHOUSE_STAFF";
    private static final String REPORT_VIEWER = "REPORT_VIEWER";

    private final JdbcTemplate jdbcTemplate;

    // Chọn tool tương ứng với intent và thực thi truy vấn.
    public AiToolResult execute(AiIntentResult route) {
        AiIntent intent = route == null || route.getIntent() == null ? AiIntent.UNSUPPORTED : route.getIntent();
        Map<String, Object> params = route == null ? Map.of() : route.safeParameters();

        if (!isIntentAllowed(intent)) {
            return AiToolResult.message("Authorization.forbidden",
                    "Bạn không có quyền truy xuất dữ liệu này qua AI.");
        }

        return switch (intent) {
            case WAREHOUSE_COUNT -> AiToolResult.data("WarehouseTool.countWarehouses", countWarehouses());
            case WAREHOUSE_LIST -> AiToolResult.data("WarehouseTool.listWarehouses", listWarehouses());
            case WAREHOUSE_DETAIL -> AiToolResult.data("WarehouseTool.getWarehouseDetail", getWarehouseDetail(params));
            case PRODUCT_COUNT -> AiToolResult.data("ProductTool.countProducts", countProducts(params));
            case PRODUCT_LIST -> AiToolResult.data("ProductTool.listProducts", listProducts(params));
            case PRODUCT_DETAIL -> AiToolResult.data("ProductTool.getProductDetail", getProductDetail(params));
            case SUPPLIER_LIST -> AiToolResult.data("SupplierTool.listSuppliers", listSuppliers(params));
            case SUPPLIER_SEARCH -> AiToolResult.data("SupplierTool.searchSuppliers", listSuppliers(params));
            case SUPPLIER_DETAIL -> AiToolResult.data("SupplierTool.getSupplierDetail", getSupplierDetail(params));
            case SUPPLIER_TOP -> AiToolResult.data("SupplierTool.getTopSuppliers", getTopSuppliers(params));
            case CUSTOMER_LIST -> AiToolResult.data("CustomerTool.listCustomers", listCustomers(params));
            case CUSTOMER_SEARCH -> AiToolResult.data("CustomerTool.searchCustomers", listCustomers(params));
            case CUSTOMER_DETAIL -> AiToolResult.data("CustomerTool.getCustomerDetail", getCustomerDetail(params));
            case LOCATION_SEARCH -> AiToolResult.data("LocationTool.searchLocations", searchLocations(params));
            case LOCATION_COUNT -> AiToolResult.data("LocationTool.countLocations", countLocations(params));
            case BEST_HEAVY_LOCATION ->
                AiToolResult.data("LocationTool.getBestHeavyLocation", getBestHeavyLocations(params));
            case STOCK_BY_PRODUCT -> getStockByProductResult(params);
            case STOCK_TOTAL -> AiToolResult.data("StockTool.getStockTotal", getStockTotal());
            case STOCK_LOWEST -> AiToolResult.data("StockTool.getLowestStock", getLowestStock());
            case STOCK_HIGHEST -> AiToolResult.data("StockTool.getHighestStock", getHighestStock());
            case PRODUCT_BY_BARCODE -> AiToolResult.data("StockTool.getProductByBarcode", getProductByBarcode(params));
            case LOT_TRACKED_COUNT -> AiToolResult.data("ProductTool.getLotTrackedCount", getLotTrackedCount());
            case STOCK_BELOW_THRESHOLD ->
                AiToolResult.data("StockTool.getStockBelowThreshold", getStockBelowThreshold(params));
            case WAREHOUSE_STOCK_SUMMARY ->
                AiToolResult.data("StockTool.getWarehouseStockSummary", getWarehouseStockSummary(params));
            case LOW_STOCK -> AiToolResult.data("StockTool.getLowStock", getLowStock());
            case NEAR_EXPIRY -> AiToolResult.data("StockTool.getNearExpiry", getNearExpiry(params));
            case STOCK_MOVEMENT_HISTORY ->
                AiToolResult.data("StockTool.getMovementHistory", getStockMovementHistory(params));
            case STOCK_TRANSFER -> AiToolResult.message("StockTool.transferGuide", getStockTransferGuide(params));
            case INVENTORY_ADJUSTMENT ->
                AiToolResult.message("StockTool.adjustmentGuide", getInventoryAdjustmentGuide(params));
            case INVENTORY_VALUE -> AiToolResult.data("StockTool.getInventoryValue", getInventoryValue());
            case PENDING_PUTAWAY -> AiToolResult.data("InboundTool.getPendingPutaway", getPendingPutaway());
            case PUTAWAY_BY_WAREHOUSE ->
                AiToolResult.data("InboundTool.getPutawayByWarehouse", getPutawayByWarehouse());
            case INBOUND_TODAY -> AiToolResult.data("InboundTool.getInboundToday", getInboundToday());
            case LATEST_INBOUND -> AiToolResult.data("InboundTool.getLatestInbound", getLatestInbound());
            case PENDING_PO_RECEIPT -> AiToolResult.data("InboundTool.getPendingPoReceipt", getPendingPoReceipt());
            case PURCHASE_ORDER_STATUS ->
                countOnly(params)
                        ? AiToolResult.data("InboundTool.getPurchaseOrderStatusCount", getPurchaseOrderCount(params))
                        : AiToolResult.data("InboundTool.getPurchaseOrderStatus", getPurchaseOrders(params));
            case PURCHASE_ORDER_DETAIL ->
                AiToolResult.data("InboundTool.getPurchaseOrderDetail", getPurchaseOrderDetail(params));
            case PURCHASE_ORDER_APPROVAL_AUDIT ->
                AiToolResult.data("InboundTool.getPurchaseOrderApprovalAudit", getPurchaseOrderApprovalAudit(params));
            case OUTBOUND_PRIORITY ->
                AiToolResult.data("OutboundTool.getPrioritySalesOrders", getPrioritySalesOrders());
            case PACKING_STATUS -> AiToolResult.data("OutboundTool.getPackingStatus", getPackingStatus());
            case PICKING_TOP -> AiToolResult.data("OutboundTool.getPickingTop", getPickingTop());
            case PICKING_STATUS -> AiToolResult.data("OutboundTool.getPickingStatus", getPickingStatus(params));
            case SALES_TOP -> AiToolResult.data("OutboundTool.getSalesTop", getSalesTop());
            case SALES_ORDER_DETAIL ->
                AiToolResult.data("OutboundTool.getSalesOrderDetail", getSalesOrderDetail(params));
            case SALES_ORDER_STATUS ->
                AiToolResult.data("OutboundTool.getSalesOrderStatus", getSalesOrderStatus(params));
            case FLOW_REPORT -> AiToolResult.data("ReportTool.getFlowReport", getFlowReport());
            case ACTIVE_CYCLE_COUNTS -> AiToolResult.data("CycleCountTool.getActive", getActiveCycleCounts());
            case CYCLE_COUNT_VARIANCE -> AiToolResult.data("CycleCountTool.getVariance", getCycleCountVariance(params));
            case RMA_PENDING -> AiToolResult.data("InboundTool.getPendingRma", getPendingRma());
            case DAILY_TASKS -> AiToolResult.data("ReportTool.getDailyTasks", getDailyTasks());
            case REPORT_SUMMARY -> AiToolResult.data("ReportTool.getOperationalSummary", getOperationalSummary());
            case INBOUND_REPORT -> AiToolResult.data("ReportTool.getInboundReport", getInboundReport(params));
            case OUTBOUND_REPORT -> AiToolResult.data("ReportTool.getOutboundReport", getOutboundReport(params));
            case MONTHLY_REPORT -> AiToolResult.data("ReportTool.getMonthlyReport", getMonthlyReport());
            case MONTH_OVER_MONTH_FLOW -> AiToolResult.data("ReportTool.getMonthOverMonthFlow", getMonthOverMonthFlow());
            case GLOBAL_SEARCH -> AiToolResult.data("SearchTool.globalSearch", getGlobalSearch(params));
            case AUDIT_LOG -> {
                if (!hasAuthority(ADMIN)) {
                    yield AiToolResult.message("AuditTool.forbidden", "Bạn không có quyền xem nhật ký hệ thống.");
                }
                yield AiToolResult.data("AuditTool.getAuditLogs", getAuditLogs(params));
            }
            case AI_AUDIT_LOG -> {
                if (!hasAuthority(ADMIN)) {
                    yield AiToolResult.message("AuditTool.forbidden", "Bạn không có quyền xem nhật ký AI.");
                }
                yield AiToolResult.data("AuditTool.getAiAuditLogs", getAiAuditLogs(params));
            }
            case GENERAL_GUIDE -> AiToolResult.message("GeneralGuide", getGuide(params));
            case AMBIGUOUS -> AiToolResult.message("Clarification",
                    "Bạn vui lòng nói rõ thêm mã kho, SKU, đơn hàng hoặc khoảng thời gian cần kiểm tra.");
            case UNSUPPORTED -> AiToolResult.message("Unsupported", getUnsupportedReply(params));
            default -> AiToolResult.message("UnsupportedIntent",
                    "Intent này đã được khai báo nhưng backend chưa có tool xử lý dữ liệu tương ứng.");
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

    // Đếm tổng số sản phẩm trong hệ thống.
    private Map<String, Object> countProducts(Map<String, Object> params) {
        String query = firstText(params, "query");
        if (asksByWarehouse(query)) {
            List<Map<String, Object>> byWarehouse = jdbcTemplate.queryForList("""
                    SELECT
                        w.code AS warehouse_code,
                        w.name AS warehouse_name,
                        COUNT(DISTINCT sl.product_id) AS product_count
                    FROM warehouses w
                    LEFT JOIN stock_levels sl ON sl.warehouse_id = w.id
                    GROUP BY w.id, w.code, w.name
                    ORDER BY w.code ASC
                    """);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long.class));
            result.put("byWarehouse", byWarehouse);
            result.put("filtered", false);
            return result;
        }

        Map<String, Object> totalRow = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total
                FROM products
                """);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", totalRow.get("total"));
        result.put("filtered", false);
        return result;
    }

    // Đếm và lấy danh sách sản phẩm cơ bản cho câu hỏi "danh sách sản phẩm".
    private Map<String, Object> listProducts(Map<String, Object> params) {
        String keyword = firstText(params, "query", "keyword", "product");
        String status = firstText(params, "status");
        String like = StringUtils.hasText(keyword) ? like(cleanProductKeyword(keyword)) : null;

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        if (StringUtils.hasText(status)) {
            where.append(" AND LOWER(p.status) = LOWER(?)");
            args.add(status);
        }
        if (StringUtils.hasText(like)) {
            where.append(" AND (LOWER(p.sku) LIKE ? OR LOWER(p.name) LIKE ? OR LOWER(c.name) LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
        }

        Map<String, Object> countRow = jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) AS total
                    FROM products p
                    LEFT JOIN categories c ON c.id = p.category_id
                    LEFT JOIN suppliers s ON s.id = p.primary_supplier_id
                    %s
                """.formatted(where), args.toArray());

        List<Map<String, Object>> items = jdbcTemplate.queryForList(("""
                SELECT
                    p.sku,
                    p.name AS product_name,
                    c.name AS category_name,
                    s.name AS supplier_name,
                    p.status,
                    p.min_stock_qty,
                    p.is_lot_tracked,
                    p.is_expiry_tracked,
                    p.updated_at
                FROM products p
                LEFT JOIN categories c ON c.id = p.category_id
                LEFT JOIN suppliers s ON s.id = p.primary_supplier_id
                %s
                ORDER BY p.updated_at DESC NULLS LAST, p.name ASC
                LIMIT %d
                """.formatted(where, DEFAULT_LIMIT)), args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", countRow.get("total"));
        result.put("items", items);
        result.put("limit", DEFAULT_LIMIT);
        result.put("filtered", StringUtils.hasText(keyword) || StringUtils.hasText(status));
        return result;
    }

    private List<Map<String, Object>> getProductDetail(Map<String, Object> params) {
        String sku = firstText(params, "sku", "code");
        if (!StringUtils.hasText(sku)) {
            sku = resolveProductSku(firstText(params, "product", "query"));
        }
        String keyword = firstText(params, "product", "query");

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE p.status IS NOT NULL ");
        if (StringUtils.hasText(sku)) {
            where.append(" AND LOWER(p.sku) = LOWER(?)");
            args.add(sku);
        } else if (StringUtils.hasText(keyword)) {
            where.append(" AND (LOWER(p.sku) LIKE ? OR LOWER(p.name) LIKE ? OR LOWER(p.barcode_ean13) LIKE ?)");
            String like = like(cleanProductKeyword(keyword));
            args.add(like);
            args.add(like);
            args.add(like);
        }

        return jdbcTemplate.queryForList((""" 
                SELECT
                    p.sku,
                    p.barcode_ean13,
                    p.name AS product_name,
                    c.name AS category_name,
                    s.code AS supplier_code,
                    s.name AS supplier_name,
                    p.base_unit,
                    p.weight_kg,
                    p.min_stock_qty,
                    p.is_lot_tracked,
                    p.is_expiry_tracked,
                    p.status,
                    COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(sl.qty_reserved), 0) AS qty_reserved,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available
                FROM products p
                LEFT JOIN categories c ON c.id = p.category_id
                LEFT JOIN suppliers s ON s.id = p.primary_supplier_id
                LEFT JOIN stock_levels sl ON sl.product_id = p.id
                %s
                GROUP BY p.id, p.sku, p.barcode_ean13, p.name, c.name, s.code, s.name,
                         p.base_unit, p.weight_kg, p.min_stock_qty, p.is_lot_tracked,
                         p.is_expiry_tracked, p.status
                ORDER BY p.updated_at DESC NULLS LAST, p.name ASC
                LIMIT 10
                """.formatted(where)), args.toArray());
    }

    private Map<String, Object> listSuppliers(Map<String, Object> params) {
        String keyword = firstText(params, "query", "supplier", "code");
        String status = firstText(params, "status");
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        if (StringUtils.hasText(status)) {
            where.append(" AND LOWER(status) = LOWER(?)");
            args.add(status);
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (LOWER(code) LIKE ? OR LOWER(name) LIKE ? OR LOWER(contact_name) LIKE ? OR LOWER(contact_phone) LIKE ? OR LOWER(contact_email) LIKE ?)");
            String like = like(keyword);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Map<String, Object> count = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total FROM suppliers " + where, args.toArray());
        List<Map<String, Object>> items = jdbcTemplate.queryForList((""" 
                SELECT code, name, tax_code, contact_name, contact_phone, contact_email,
                       address, payment_terms, lead_time_days, status, updated_at
                FROM suppliers
                %s
                ORDER BY updated_at DESC NULLS LAST, name ASC
                LIMIT %d
                """.formatted(where, DEFAULT_LIMIT)), args.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", count.get("total"));
        result.put("items", items);
        return result;
    }

    private List<Map<String, Object>> getSupplierDetail(Map<String, Object> params) {
        String keyword = firstText(params, "supplier", "code", "query");
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT code, name, tax_code, contact_name, contact_phone, contact_email,
                       address, payment_terms, lead_time_days, status, created_at, updated_at
                FROM suppliers
                WHERE LOWER(code) = LOWER(?)
                   OR LOWER(name) LIKE ?
                   OR LOWER(tax_code) = LOWER(?)
                   OR LOWER(contact_phone) LIKE ?
                   OR LOWER(contact_email) LIKE ?
                ORDER BY updated_at DESC NULLS LAST, name ASC
                LIMIT 10
                """, keyword, like, keyword, like, like);
    }

    private List<Map<String, Object>> getTopSuppliers(Map<String, Object> params) {
        String dateRange = firstText(params, "dateRange");
        if (!StringUtils.hasText(dateRange)) {
            dateRange = "THIS_MONTH";
        }
        List<Object> args = new ArrayList<>();
        StringBuilder filter = new StringBuilder(" WHERE 1 = 1 ");
        appendDateRange(filter, args, "po.created_at", dateRange);
        return jdbcTemplate.queryForList((""" 
                SELECT
                    s.code AS supplier_code,
                    s.name AS supplier_name,
                    COUNT(DISTINCT po.id) AS purchase_order_count,
                    COALESCE(SUM(po.total_amount), 0) AS total_amount,
                    COALESCE(SUM(pi.ordered_qty), 0) AS ordered_qty
                FROM purchase_orders po
                JOIN suppliers s ON s.id = po.supplier_id
                LEFT JOIN po_items pi ON pi.po_id = po.id
                %s
                GROUP BY s.code, s.name
                ORDER BY purchase_order_count DESC, total_amount DESC, s.name ASC
                LIMIT 10
                """.formatted(filter)), args.toArray());
    }

    private Map<String, Object> listCustomers(Map<String, Object> params) {
        String keyword = firstText(params, "query", "customer", "code");
        String status = firstText(params, "status");
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        if (StringUtils.hasText(status)) {
            boolean active = !"inactive".equalsIgnoreCase(status) && !"false".equalsIgnoreCase(status);
            where.append(" AND is_active = ?");
            args.add(active);
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (LOWER(code) LIKE ? OR LOWER(name) LIKE ? OR LOWER(contact_name) LIKE ? OR LOWER(phone) LIKE ? OR LOWER(email) LIKE ?)");
            String like = like(keyword);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Map<String, Object> count = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total FROM customers " + where, args.toArray());
        List<Map<String, Object>> items = jdbcTemplate.queryForList((""" 
                SELECT code, name, contact_name, phone, email, tax_code, is_active, updated_at
                FROM customers
                %s
                ORDER BY updated_at DESC NULLS LAST, name ASC
                LIMIT %d
                """.formatted(where, DEFAULT_LIMIT)), args.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", count.get("total"));
        result.put("items", items);
        return result;
    }

    private List<Map<String, Object>> getCustomerDetail(Map<String, Object> params) {
        String keyword = firstText(params, "customer", "code", "query");
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT code, name, contact_name, phone, email, tax_code, address,
                       notes, is_active, created_at, updated_at
                FROM customers
                WHERE LOWER(code) = LOWER(?)
                   OR LOWER(name) LIKE ?
                   OR LOWER(contact_name) LIKE ?
                   OR LOWER(phone) LIKE ?
                   OR LOWER(email) LIKE ?
                ORDER BY updated_at DESC NULLS LAST, name ASC
                LIMIT 10
                """, keyword, like, like, like, like);
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
        ResolvedWarehouse resolved = resolveWarehouse(params);
        if (resolved != null) {
            return jdbcTemplate.queryForList("""
                    SELECT code, name, address, manager_name, timezone, is_active, created_at, updated_at
                    FROM warehouses
                    WHERE LOWER(code) = LOWER(?)
                    LIMIT 1
                    """, resolved.code());
        }

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
        ResolvedWarehouse resolvedWarehouse = resolveWarehouse(params);
        String warehouse = resolvedWarehouse == null ? firstText(params, "warehouseCode", "warehouse")
                : resolvedWarehouse.code();
        String query = firstText(params, "query");
        String normalizedQuery = normalize(query);
        String keyword = firstText(params, "location", "code");
        String status = normalizedQuery.contains("maintenance") ? "MAINTENANCE"
                : normalizedQuery.contains("available") ? "AVAILABLE" : null;

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
        if (StringUtils.hasText(status)) {
            sql.append(" AND LOWER(l.status) = LOWER(?)");
            args.add(status);
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

    private Map<String, Object> countLocations(Map<String, Object> params) {
        ResolvedWarehouse warehouse = resolveWarehouse(params);
        if (warehouse == null && hasWarehouseHint(params)) {
            return Map.of("warehouse_found", false);
        }
        if (warehouse == null) {
            return jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) AS total,
                           COUNT(*) FILTER (WHERE status = 'AVAILABLE') AS available,
                           COUNT(*) FILTER (WHERE status = 'MAINTENANCE') AS maintenance
                    FROM locations
                    """);
        }
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT ? AS warehouse_code,
                       ? AS warehouse_name,
                       COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE l.location_type = 'STORAGE') AS storage,
                       COUNT(*) FILTER (WHERE l.status = 'AVAILABLE') AS available,
                       COUNT(*) FILTER (WHERE l.status = 'MAINTENANCE') AS maintenance
                FROM locations l
                JOIN warehouses w ON w.id = l.warehouse_id
                WHERE LOWER(w.code) = LOWER(?)
                """, warehouse.code(), warehouse.name(), warehouse.code());
        row.put("warehouse_found", true);
        return row;
    }

    private List<Map<String, Object>> getBestHeavyLocations(Map<String, Object> params) {
        ResolvedWarehouse warehouse = resolveWarehouse(params);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    w.code AS warehouse_code,
                    l.code AS location_code,
                    l.zone,
                    l.location_type,
                    l.status,
                    l.max_weight_kg,
                    l.max_volume_cm3
                FROM locations l
                JOIN warehouses w ON w.id = l.warehouse_id
                WHERE l.status = 'AVAILABLE'
                  AND l.location_type = 'STORAGE'
                """);
        if (warehouse != null) {
            sql.append(" AND LOWER(w.code) = LOWER(?)");
            args.add(warehouse.code());
        }
        sql.append(" ORDER BY l.max_weight_kg DESC NULLS LAST, l.max_volume_cm3 DESC NULLS LAST, l.code ASC LIMIT 10");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    // Tổng hợp tồn kho toàn hệ thống.
    private Map<String, Object> getStockTotal() {
        return jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS stock_lines,
                    COUNT(DISTINCT product_id) AS stocked_skus,
                    COALESCE(SUM(qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(qty_reserved), 0) AS qty_reserved,
                    COALESCE(SUM(qty_available), 0) AS qty_available
                FROM stock_levels
                """);
    }

    // Lấy các sản phẩm có tồn khả dụng thấp nhất.
    private List<Map<String, Object>> getLowestStock() {
        return jdbcTemplate.queryForList("""
                SELECT
                    p.sku,
                    p.name AS product_name,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available,
                    COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(sl.qty_reserved), 0) AS qty_reserved,
                    p.min_stock_qty
                FROM products p
                LEFT JOIN stock_levels sl ON sl.product_id = p.id
                WHERE p.status = 'ACTIVE'
                GROUP BY p.id, p.sku, p.name, p.min_stock_qty
                ORDER BY qty_available ASC, p.name ASC
                LIMIT 10
                """);
    }

    private List<Map<String, Object>> getHighestStock() {
        return jdbcTemplate.queryForList("""
                SELECT
                    p.sku,
                    p.name AS product_name,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available,
                    COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(sl.qty_reserved), 0) AS qty_reserved
                FROM products p
                LEFT JOIN stock_levels sl ON sl.product_id = p.id
                WHERE p.status = 'ACTIVE'
                GROUP BY p.id, p.sku, p.name
                ORDER BY qty_on_hand DESC, p.name ASC
                LIMIT 10
                """);
    }

    private List<Map<String, Object>> getProductByBarcode(Map<String, Object> params) {
        String barcode = extractFirstNumber(firstText(params, "query"));
        if (!StringUtils.hasText(barcode)) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT
                    p.sku,
                    p.barcode_ean13,
                    p.name AS product_name,
                    COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(sl.qty_reserved), 0) AS qty_reserved,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available
                FROM products p
                LEFT JOIN stock_levels sl ON sl.product_id = p.id
                WHERE p.barcode_ean13 = ?
                GROUP BY p.id, p.sku, p.barcode_ean13, p.name
                LIMIT 10
                """, barcode);
    }

    private Map<String, Object> getLotTrackedCount() {
        return jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE is_lot_tracked = TRUE) AS lot_tracked,
                    COUNT(*) FILTER (WHERE is_expiry_tracked = TRUE) AS expiry_tracked
                FROM products
                WHERE status = 'ACTIVE'
                """);
    }

    private List<Map<String, Object>> getStockBelowThreshold(Map<String, Object> params) {
        int threshold = intValue(extractFirstNumber(firstText(params, "query")), 10);
        return jdbcTemplate.queryForList("""
                SELECT
                    p.sku,
                    p.name AS product_name,
                    COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available
                FROM products p
                LEFT JOIN stock_levels sl ON sl.product_id = p.id
                WHERE p.status = 'ACTIVE'
                GROUP BY p.id, p.sku, p.name
                HAVING COALESCE(SUM(sl.qty_on_hand), 0) < ?
                ORDER BY qty_on_hand ASC, p.name ASC
                LIMIT 50
                """, threshold);
    }

    // Tổng hợp tồn kho theo kho hoặc theo nhóm sản phẩm trong một kho.
    private List<Map<String, Object>> getWarehouseStockSummary(Map<String, Object> params) {
        ResolvedWarehouse warehouse = resolveWarehouse(params);
        String query = normalize(firstText(params, "query"));
        if (warehouse != null && (query.contains("iphone") || query.contains("laptop"))) {
            List<Map<String, Object>> rows = new ArrayList<>();
            if (query.contains("iphone")) {
                rows.add(stockGroupInWarehouse("iPhone", warehouse.code(), "LOWER(p.name) LIKE '%iphone%'"));
            }
            if (query.contains("laptop")) {
                rows.add(stockGroupInWarehouse("Laptop", warehouse.code(), """
                        (LOWER(p.name) LIKE '%laptop%'
                         OR LOWER(p.name) LIKE '%macbook%'
                         OR LOWER(c.name) LIKE '%laptop%')
                        """));
            }
            return rows;
        }

        if (warehouse != null) {
            return jdbcTemplate.queryForList("""
                    SELECT
                        w.code AS warehouse_code,
                        w.name AS warehouse_name,
                        COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                        COALESCE(SUM(sl.qty_reserved), 0) AS qty_reserved,
                        COALESCE(SUM(sl.qty_available), 0) AS qty_available,
                        COUNT(DISTINCT sl.product_id) AS stocked_skus
                    FROM warehouses w
                    LEFT JOIN stock_levels sl ON sl.warehouse_id = w.id
                    WHERE LOWER(w.code) = LOWER(?)
                    GROUP BY w.id, w.code, w.name
                    LIMIT 1
                    """, warehouse.code());
        }

        return jdbcTemplate.queryForList("""
                SELECT
                    w.code AS warehouse_code,
                    w.name AS warehouse_name,
                    COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(sl.qty_reserved), 0) AS qty_reserved,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available,
                    COUNT(DISTINCT sl.product_id) AS stocked_skus
                FROM warehouses w
                LEFT JOIN stock_levels sl ON sl.warehouse_id = w.id
                WHERE w.is_active = TRUE
                GROUP BY w.id, w.code, w.name
                ORDER BY qty_on_hand DESC, w.code ASC
                LIMIT 10
                """);
    }

    private Map<String, Object> stockGroupInWarehouse(String label, String warehouseCode, String productCondition) {
        return jdbcTemplate.queryForMap("""
                SELECT
                    ? AS product_group,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name,
                    COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(sl.qty_reserved), 0) AS qty_reserved,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available,
                    COUNT(DISTINCT p.id) AS matched_products
                FROM warehouses w
                JOIN products p ON p.status = 'ACTIVE'
                LEFT JOIN categories c ON c.id = p.category_id
                LEFT JOIN stock_levels sl ON sl.product_id = p.id AND sl.warehouse_id = w.id
                WHERE LOWER(w.code) = LOWER(?)
                  AND %s
                GROUP BY w.id, w.code, w.name
                """.formatted(productCondition), label, warehouseCode);
    }

    // Resolve sản phẩm/kho trước khi tra tồn kho để phân biệt sai kho và không có
    // tồn.
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
        if (rows.isEmpty() && StringUtils.hasText(text(resolvedParams.get("sku")))) {
            rows = getProductStockFallback(resolvedParams);
        }
        if (rows.isEmpty() && warehouse != null) {
            return AiToolResult.message("StockTool.getStockByProduct",
                    "Kho " + warehouse.code()
                            + " có tồn tại, nhưng tôi chưa tìm thấy tồn kho phù hợp với sản phẩm hoặc SKU bạn hỏi trong kho này.");
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
            sql.append(" AND (LOWER(p.sku) = LOWER(?) OR LOWER(p.sku) LIKE LOWER(?))");
            args.add(sku);
            args.add(sku + "%");
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

    private List<Map<String, Object>> getProductStockFallback(Map<String, Object> params) {
        String sku = text(params.get("sku"));
        String warehouse = firstText(params, "warehouseCode", "warehouse");
        if (!StringUtils.hasText(sku)) {
            return List.of();
        }
        if (StringUtils.hasText(warehouse)) {
            return jdbcTemplate.queryForList("""
                    SELECT
                        p.sku,
                        p.name AS product_name,
                        w.code AS warehouse_code,
                        w.name AS warehouse_name,
                        NULL AS location_code,
                        NULL AS lot_number,
                        NULL AS expiry_date,
                        0 AS qty_on_hand,
                        0 AS qty_reserved,
                        0 AS qty_available,
                        NULL AS updated_at
                    FROM products p
                    JOIN warehouses w ON LOWER(w.code) = LOWER(?)
                    WHERE LOWER(p.sku) = LOWER(?) OR LOWER(p.sku) LIKE LOWER(?)
                    LIMIT 1
                    """, warehouse, sku, sku + "%");
        }
        return jdbcTemplate.queryForList("""
                SELECT
                    p.sku,
                    p.name AS product_name,
                    NULL AS warehouse_code,
                    NULL AS warehouse_name,
                    NULL AS location_code,
                    NULL AS lot_number,
                    NULL AS expiry_date,
                    COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(sl.qty_reserved), 0) AS qty_reserved,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available,
                    MAX(sl.updated_at) AS updated_at
                FROM products p
                LEFT JOIN stock_levels sl ON sl.product_id = p.id
                WHERE LOWER(p.sku) = LOWER(?) OR LOWER(p.sku) LIKE LOWER(?)
                GROUP BY p.id, p.sku, p.name
                LIMIT 1
                """, sku, sku + "%");
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
                LEFT JOIN stock_levels sl ON sl.product_id = p.id
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

    private List<Map<String, Object>> getStockMovementHistory(Map<String, Object> params) {
        String sku = firstText(params, "sku");
        if (!StringUtils.hasText(sku)) {
            sku = resolveProductSku(firstText(params, "product", "query"));
        }
        ResolvedWarehouse warehouse = resolveWarehouse(params);
        String movementType = firstText(params, "status", "movementType");
        String dateRange = firstText(params, "dateRange");

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    sm.created_at,
                    sm.movement_type,
                    sm.qty_change,
                    sm.qty_after,
                    sm.reserved_change,
                    sm.reserved_after,
                    sm.lot_number,
                    sm.reason,
                    sm.reference_type,
                    sm.created_by,
                    w.code AS warehouse_code,
                    l.code AS location_code,
                    COALESCE(p.sku, sm.product_id::text) AS sku,
                    COALESCE(p.name, 'Sản phẩm ' || sm.product_id::text) AS product_name
                FROM stock_movements sm
                LEFT JOIN warehouses w ON w.id = sm.warehouse_id
                LEFT JOIN locations l ON l.id = sm.location_id
                LEFT JOIN products p ON p.id = sm.product_id
                WHERE 1 = 1
                """);
        if (StringUtils.hasText(sku)) {
            sql.append(" AND LOWER(p.sku) = LOWER(?)");
            args.add(sku);
        }
        if (warehouse != null) {
            sql.append(" AND LOWER(w.code) = LOWER(?)");
            args.add(warehouse.code());
        }
        if (StringUtils.hasText(movementType)) {
            sql.append(" AND LOWER(sm.movement_type) LIKE ?");
            args.add(like(movementType));
        }
        appendDateRange(sql, args, "sm.created_at", dateRange);
        sql.append(" ORDER BY sm.created_at DESC LIMIT ").append(DEFAULT_LIMIT);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private String getStockTransferGuide(Map<String, Object> params) {
        return """
                Tôi không tự chuyển kho qua chat. Để chuyển tồn kho, hãy mở màn hình chuyển kho/stock transfer, chọn SKU, kho/vị trí nguồn, kho/vị trí đích, số lượng và lý do; sau đó kiểm tra lại tồn khả dụng trước khi xác nhận.
                """.trim();
    }

    private String getInventoryAdjustmentGuide(Map<String, Object> params) {
        return """
                Tôi không tự điều chỉnh tồn kho qua chat. Để điều chỉnh tồn, hãy kiểm tra SKU/kho/vị trí hiện tại, tạo phiếu điều chỉnh với lý do rõ ràng, nhập số lượng tăng hoặc giảm và chỉ xác nhận khi có quyền duyệt.
                """.trim();
    }

    private Map<String, Object> getInventoryValue() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", jdbcTemplate.queryForMap("""
                WITH product_stock AS (
                    SELECT sl.product_id,
                           COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                           COALESCE(SUM(sl.qty_available), 0) AS qty_available
                    FROM stock_levels sl
                    GROUP BY sl.product_id
                ),
                product_cost AS (
                    SELECT pi.product_id,
                           CASE
                               WHEN COALESCE(SUM(pi.ordered_qty), 0) > 0
                                   THEN COALESCE(SUM(pi.unit_price * pi.ordered_qty), 0) / SUM(pi.ordered_qty)
                               ELSE COALESCE(AVG(pi.unit_price), 0)
                           END AS avg_unit_cost
                    FROM po_items pi
                    WHERE pi.unit_price IS NOT NULL
                    GROUP BY pi.product_id
                )
                SELECT
                    COUNT(*) AS valued_products,
                    COALESCE(SUM(ps.qty_on_hand), 0) AS qty_on_hand,
                    COALESCE(SUM(ps.qty_available), 0) AS qty_available,
                    COALESCE(SUM(ps.qty_on_hand * COALESCE(pc.avg_unit_cost, 0)), 0) AS inventory_value
                FROM product_stock ps
                LEFT JOIN product_cost pc ON pc.product_id = ps.product_id
                """));
        result.put("top_products", jdbcTemplate.queryForList("""
                WITH product_stock AS (
                    SELECT sl.product_id,
                           COALESCE(SUM(sl.qty_on_hand), 0) AS qty_on_hand,
                           COALESCE(SUM(sl.qty_available), 0) AS qty_available
                    FROM stock_levels sl
                    GROUP BY sl.product_id
                ),
                product_cost AS (
                    SELECT pi.product_id,
                           CASE
                               WHEN COALESCE(SUM(pi.ordered_qty), 0) > 0
                                   THEN COALESCE(SUM(pi.unit_price * pi.ordered_qty), 0) / SUM(pi.ordered_qty)
                               ELSE COALESCE(AVG(pi.unit_price), 0)
                           END AS avg_unit_cost
                    FROM po_items pi
                    WHERE pi.unit_price IS NOT NULL
                    GROUP BY pi.product_id
                )
                SELECT
                    p.sku,
                    p.name AS product_name,
                    ps.qty_on_hand,
                    ps.qty_available,
                    COALESCE(pc.avg_unit_cost, 0) AS avg_unit_cost,
                    ps.qty_on_hand * COALESCE(pc.avg_unit_cost, 0) AS inventory_value
                FROM product_stock ps
                JOIN products p ON p.id = ps.product_id
                LEFT JOIN product_cost pc ON pc.product_id = ps.product_id
                ORDER BY inventory_value DESC, p.name ASC
                LIMIT 5
                """));
        return result;
    }

    // Lấy các task putaway đang chờ xử lý.
    private List<Map<String, Object>> getPendingPutaway() {
        return jdbcTemplate.queryForList("""
                SELECT
                    pt.id,
                    pt.status,
                    pt.qty_to_putaway,
                    ir.receipt_number,
                    COALESCE(p.sku, pt.product_id::text) AS sku,
                    COALESCE(p.name, 'Sản phẩm ' || pt.product_id::text) AS product_name,
                    suggested.code AS suggested_location,
                    actual.code AS actual_location,
                    pt.completed_at
                FROM putaway_tasks pt
                LEFT JOIN products p ON p.id = pt.product_id
                LEFT JOIN inbound_receipts ir ON ir.id = pt.inbound_receipt_id
                LEFT JOIN locations suggested ON suggested.id = pt.suggested_location_id
                LEFT JOIN locations actual ON actual.id = pt.actual_location_id
                WHERE pt.status IN ('PENDING', 'IN_PROGRESS', 'ASSIGNED')
                ORDER BY pt.status ASC, pt.id ASC
                LIMIT 50
                """);
    }

    // Lấy phiếu nhập/dòng hàng nhập gần nhất cùng nhà cung cấp.
    private List<Map<String, Object>> getLatestInbound() {
        return jdbcTemplate.queryForList("""
                SELECT
                    ir.receipt_number,
                    ir.status AS receipt_status,
                    ir.received_date,
                    po.po_number,
                    po.status AS po_status,
                    s.code AS supplier_code,
                    s.name AS supplier_name,
                    w.code AS warehouse_code,
                    COALESCE(p.sku, iri.product_sku) AS sku,
                    COALESCE(p.name, iri.product_sku) AS product_name,
                    iri.received_qty
                FROM inbound_receipts ir
                JOIN purchase_orders po ON po.id = ir.po_id
                LEFT JOIN suppliers s ON s.id = po.supplier_id
                LEFT JOIN warehouses w ON w.id = ir.warehouse_id
                LEFT JOIN inbound_receipt_items iri ON iri.receipt_id = ir.id
                LEFT JOIN products p ON p.id = iri.product_id
                ORDER BY ir.received_date DESC, ir.created_at DESC, ir.receipt_number DESC
                LIMIT 10
                """);
    }

    private List<Map<String, Object>> getInboundToday() {
        return jdbcTemplate.queryForList("""
                SELECT
                    ir.receipt_number,
                    ir.status AS receipt_status,
                    ir.received_date,
                    po.po_number,
                    s.name AS supplier_name,
                    COALESCE(p.sku, iri.product_sku) AS sku,
                    COALESCE(p.name, iri.product_sku) AS product_name,
                    iri.received_qty
                FROM inbound_receipts ir
                JOIN purchase_orders po ON po.id = ir.po_id
                LEFT JOIN suppliers s ON s.id = po.supplier_id
                LEFT JOIN inbound_receipt_items iri ON iri.receipt_id = ir.id
                LEFT JOIN products p ON p.id = iri.product_id
                WHERE ir.received_date = CURRENT_DATE
                ORDER BY ir.created_at DESC, ir.receipt_number DESC
                LIMIT 50
                """);
    }

    private List<Map<String, Object>> getPendingPoReceipt() {
        return jdbcTemplate.queryForList("""
                SELECT
                    po.po_number,
                    po.status,
                    po.order_date,
                    po.expected_date,
                    s.name AS supplier_name,
                    w.code AS warehouse_code,
                    COALESCE(SUM(pi.ordered_qty), 0) AS ordered_qty,
                    COALESCE(SUM(pi.received_qty), 0) AS received_qty,
                    COALESCE(SUM(pi.ordered_qty - pi.received_qty), 0) AS remaining_qty
                FROM purchase_orders po
                LEFT JOIN po_items pi ON pi.po_id = po.id
                LEFT JOIN suppliers s ON s.id = po.supplier_id
                LEFT JOIN warehouses w ON w.id = po.warehouse_id
                WHERE po.status <> 'COMPLETED'
                GROUP BY po.id, po.po_number, po.status, po.order_date, po.expected_date, s.name, w.code
                ORDER BY po.expected_date ASC NULLS LAST, po.order_date DESC
                LIMIT 50
                """);
    }

    // Tra cứu đơn nhập theo mã hoặc trạng thái chưa hoàn thành.
    private List<Map<String, Object>> getPurchaseOrders(Map<String, Object> params) {
        String code = firstText(params, "code", "query");
        String status = firstText(params, "status");
        String dateRange = firstText(params, "dateRange");
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    po.po_number,
                    po.status,
                    po.order_date,
                    po.expected_date,
                    po.total_amount,
                    s.code AS supplier_code,
                    s.name AS supplier_name,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name
                FROM purchase_orders po
                LEFT JOIN suppliers s ON s.id = po.supplier_id
                LEFT JOIN warehouses w ON w.id = po.warehouse_id
                WHERE 1 = 1
                """);
        if (StringUtils.hasText(code)) {
            sql.append(" AND LOWER(po.po_number) LIKE ?");
            args.add(like(code));
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND LOWER(po.status) = LOWER(?)");
            args.add(status);
        } else if (!StringUtils.hasText(code)) {
            sql.append(" AND po.status <> 'COMPLETED'");
        }
        appendDateRange(sql, args, "po.created_at", dateRange);
        sql.append(" ORDER BY po.expected_date ASC NULLS LAST, po.order_date DESC LIMIT ").append(DEFAULT_LIMIT);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private Map<String, Object> getPurchaseOrderCount(Map<String, Object> params) {
        String code = firstText(params, "code", "query");
        String status = firstText(params, "status");
        String dateRange = firstText(params, "dateRange");
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS total
                FROM purchase_orders po
                WHERE 1 = 1
                """);
        if (StringUtils.hasText(code)) {
            sql.append(" AND LOWER(po.po_number) LIKE ?");
            args.add(like(code));
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND LOWER(po.status) = LOWER(?)");
            args.add(status);
        } else if (!StringUtils.hasText(code)) {
            sql.append(" AND po.status <> 'COMPLETED'");
        }
        appendDateRange(sql, args, "po.created_at", dateRange);
        Map<String, Object> row = jdbcTemplate.queryForMap(sql.toString(), args.toArray());
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("status", status);
        result.put("dateRange", dateRange);
        return result;
    }

    private List<Map<String, Object>> getPurchaseOrderDetail(Map<String, Object> params) {
        String code = firstText(params, "code", "query");
        if (!StringUtils.hasText(code)) {
            return getPurchaseOrders(params);
        }
        return jdbcTemplate.queryForList("""
                SELECT
                    po.po_number,
                    po.status,
                    po.order_date,
                    po.expected_date,
                    po.total_amount,
                    s.code AS supplier_code,
                    s.name AS supplier_name,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name,
                    pi.line_number,
                    COALESCE(p.sku, pi.product_sku) AS sku,
                    COALESCE(p.name, pi.product_sku) AS product_name,
                    pi.ordered_qty,
                    pi.received_qty,
                    pi.unit_price
                FROM purchase_orders po
                LEFT JOIN suppliers s ON s.id = po.supplier_id
                LEFT JOIN warehouses w ON w.id = po.warehouse_id
                LEFT JOIN po_items pi ON pi.po_id = po.id
                LEFT JOIN products p ON p.id = pi.product_id
                WHERE LOWER(po.po_number) LIKE ?
                ORDER BY po.po_number ASC, pi.line_number ASC NULLS LAST
                LIMIT 50
                """, like(code));
    }

    private List<Map<String, Object>> getPurchaseOrderApprovalAudit(Map<String, Object> params) {
        String code = firstText(params, "code", "poId", "query");
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    entity_name AS po_number,
                    actor_name,
                    actor_email,
                    reason,
                    created_at,
                    module,
                    action_type
                FROM audit_logs
                WHERE LOWER(entity_type) = 'purchaseorder'
                  AND LOWER(action_type) = 'approve'
                """);
        if (StringUtils.hasText(code)) {
            sql.append(" AND LOWER(entity_name) LIKE ?");
            args.add(like(code));
        }
        sql.append(" ORDER BY created_at DESC LIMIT ").append(StringUtils.hasText(code) ? 10 : 20);
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

    private List<Map<String, Object>> getSalesOrderStatus(Map<String, Object> params) {
        String code = firstText(params, "code", "query");
        String status = firstText(params, "status");
        String dateRange = firstText(params, "dateRange");
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    so.so_number,
                    so.customer_name,
                    so.priority,
                    so.status,
                    so.created_at,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name,
                    COALESCE(SUM(soi.ordered_qty), 0) AS ordered_qty,
                    COALESCE(SUM(soi.shipped_qty), 0) AS shipped_qty
                FROM sales_orders so
                LEFT JOIN warehouses w ON w.id = so.warehouse_id
                LEFT JOIN sales_order_items soi ON soi.sales_order_id = so.id
                WHERE 1 = 1
                """);
        if (StringUtils.hasText(code)) {
            sql.append(" AND LOWER(so.so_number) LIKE ?");
            args.add(like(code));
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND LOWER(so.status) = LOWER(?)");
            args.add(status);
        } else if (!StringUtils.hasText(code)) {
            sql.append(" AND so.status <> 'CANCELLED'");
        }
        appendDateRange(sql, args, "so.created_at", dateRange);
        sql.append("""
                GROUP BY so.id, so.so_number, so.customer_name, so.priority, so.status,
                         so.created_at, w.code, w.name
                ORDER BY
                    CASE WHEN LOWER(?) = 'true' THEN 0 ELSE so.priority END ASC,
                    so.created_at DESC
                LIMIT ?
                """);
        args.add(Boolean.toString(Boolean.TRUE.equals(params.get("latestOnly"))));
        args.add(Boolean.TRUE.equals(params.get("latestOnly")) ? 1 : DEFAULT_LIMIT);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> getSalesOrderDetail(Map<String, Object> params) {
        String code = firstText(params, "code", "query");
        if (!StringUtils.hasText(code)) {
            return getSalesOrderStatus(params);
        }
        return jdbcTemplate.queryForList("""
                SELECT
                    so.so_number,
                    so.customer_name,
                    so.priority,
                    so.status,
                    so.created_at,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name,
                    soi.line_number,
                    COALESCE(p.sku, soi.product_sku) AS sku,
                    COALESCE(p.name, soi.product_sku) AS product_name,
                    soi.ordered_qty,
                    soi.shipped_qty,
                    soi.unit_price
                FROM sales_orders so
                LEFT JOIN warehouses w ON w.id = so.warehouse_id
                LEFT JOIN sales_order_items soi ON soi.sales_order_id = so.id
                LEFT JOIN products p ON p.id = soi.product_id
                WHERE LOWER(so.so_number) LIKE ?
                ORDER BY so.so_number ASC, soi.line_number ASC NULLS LAST
                LIMIT 50
                """, like(code));
    }

    private List<Map<String, Object>> getPackingStatus() {
        return jdbcTemplate.queryForList("""
                SELECT
                    so.so_number,
                    so.customer_name,
                    so.priority,
                    so.status,
                    so.created_at,
                    w.code AS warehouse_code
                FROM sales_orders so
                LEFT JOIN warehouses w ON w.id = so.warehouse_id
                WHERE so.status IN ('PICKING', 'PACKED')
                ORDER BY so.priority ASC, so.created_at ASC
                LIMIT 50
                """);
    }

    private List<Map<String, Object>> getPickingTop() {
        return jdbcTemplate.queryForList("""
                SELECT
                    COALESCE(p.sku, pi.product_id::text) AS sku,
                    COALESCE(p.name, 'Sản phẩm ' || pi.product_id::text) AS product_name,
                    SUM(pi.qty_to_pick) AS qty_to_pick,
                    SUM(pi.qty_picked) AS qty_picked,
                    COUNT(*) AS picking_lines
                FROM picking_items pi
                LEFT JOIN products p ON p.id = pi.product_id
                GROUP BY pi.product_id, p.sku, p.name
                ORDER BY qty_to_pick DESC, product_name ASC
                LIMIT 10
                """);
    }

    private List<Map<String, Object>> getSalesTop() {
        return jdbcTemplate.queryForList("""
                SELECT
                    COALESCE(p.sku, soi.product_sku) AS sku,
                    COALESCE(p.name, soi.product_sku) AS product_name,
                    SUM(soi.ordered_qty) AS ordered_qty,
                    SUM(soi.shipped_qty) AS shipped_qty,
                    COUNT(DISTINCT so.id) AS order_count
                FROM sales_order_items soi
                JOIN sales_orders so ON so.id = soi.sales_order_id
                LEFT JOIN products p ON p.id = soi.product_id
                WHERE so.status <> 'CANCELLED'
                GROUP BY soi.product_id, soi.product_sku, p.sku, p.name
                ORDER BY shipped_qty DESC, ordered_qty DESC, product_name ASC
                LIMIT 5
                """);
    }

    private Map<String, Object> getFlowReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("inbound", jdbcTemplate.queryForMap("""
                SELECT COUNT(DISTINCT ir.id) AS receipts,
                       COALESCE(SUM(iri.received_qty), 0) AS received_qty
                FROM inbound_receipts ir
                LEFT JOIN inbound_receipt_items iri ON iri.receipt_id = ir.id
                WHERE ir.received_date >= CURRENT_DATE - INTERVAL '7 days'
                """));
        report.put("outbound", jdbcTemplate.queryForMap("""
                SELECT COUNT(DISTINCT so.id) AS sales_orders,
                       COALESCE(SUM(soi.shipped_qty), 0) AS shipped_qty,
                       COALESCE(SUM(soi.ordered_qty), 0) AS ordered_qty
                FROM sales_orders so
                LEFT JOIN sales_order_items soi ON soi.sales_order_id = so.id
                WHERE so.created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
                  AND so.status <> 'CANCELLED'
                """));
        report.put("stock_movements", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS movements,
                       COALESCE(SUM(CASE WHEN qty_change > 0 THEN qty_change ELSE 0 END), 0) AS inbound_qty,
                       COALESCE(SUM(CASE WHEN qty_change < 0 THEN -qty_change ELSE 0 END), 0) AS outbound_qty
                FROM stock_movements
                WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
                """));
        return report;
    }

    private Map<String, Object> getInboundReport(Map<String, Object> params) {
        String dateRange = firstText(params, "dateRange");
        List<Object> args = new ArrayList<>();
        StringBuilder filter = new StringBuilder(" WHERE 1 = 1 ");
        appendDateRange(filter, args, "ir.received_date", dateRange);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", jdbcTemplate.queryForMap((""" 
                SELECT COUNT(DISTINCT ir.id) AS receipts,
                       COUNT(DISTINCT po.id) AS purchase_orders,
                       COALESCE(SUM(iri.received_qty), 0) AS received_qty
                FROM inbound_receipts ir
                LEFT JOIN purchase_orders po ON po.id = ir.po_id
                LEFT JOIN inbound_receipt_items iri ON iri.receipt_id = ir.id
                %s
                """.formatted(filter)), args.toArray()));
        report.put("top_suppliers", jdbcTemplate.queryForList((""" 
                SELECT s.code AS supplier_code, s.name AS supplier_name,
                       COUNT(DISTINCT ir.id) AS receipts,
                       COALESCE(SUM(iri.received_qty), 0) AS received_qty
                FROM inbound_receipts ir
                JOIN purchase_orders po ON po.id = ir.po_id
                LEFT JOIN suppliers s ON s.id = po.supplier_id
                LEFT JOIN inbound_receipt_items iri ON iri.receipt_id = ir.id
                %s
                GROUP BY s.code, s.name
                ORDER BY received_qty DESC, receipts DESC
                LIMIT 5
                """.formatted(filter)), args.toArray()));
        return report;
    }

    private Map<String, Object> getOutboundReport(Map<String, Object> params) {
        String dateRange = firstText(params, "dateRange");
        List<Object> args = new ArrayList<>();
        StringBuilder filter = new StringBuilder(" WHERE so.status <> 'CANCELLED' ");
        appendDateRange(filter, args, "so.created_at", dateRange);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", jdbcTemplate.queryForMap((""" 
                SELECT COUNT(DISTINCT so.id) AS sales_orders,
                       COALESCE(SUM(soi.ordered_qty), 0) AS ordered_qty,
                       COALESCE(SUM(soi.shipped_qty), 0) AS shipped_qty
                FROM sales_orders so
                LEFT JOIN sales_order_items soi ON soi.sales_order_id = so.id
                %s
                """.formatted(filter)), args.toArray()));
        report.put("by_status", jdbcTemplate.queryForList((""" 
                SELECT so.status, COUNT(DISTINCT so.id) AS sales_orders,
                       COALESCE(SUM(soi.ordered_qty), 0) AS ordered_qty,
                       COALESCE(SUM(soi.shipped_qty), 0) AS shipped_qty
                FROM sales_orders so
                LEFT JOIN sales_order_items soi ON soi.sales_order_id = so.id
                %s
                GROUP BY so.status
                ORDER BY sales_orders DESC, so.status ASC
                """.formatted(filter)), args.toArray()));
        return report;
    }

    private Map<String, Object> getMonthlyReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("stock", getStockTotal());
        report.put("inbound", getInboundReport(Map.of("dateRange", "THIS_MONTH")).get("summary"));
        report.put("outbound", getOutboundReport(Map.of("dateRange", "THIS_MONTH")).get("summary"));
        report.put("movements", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS movements,
                       COALESCE(SUM(CASE WHEN qty_change > 0 THEN qty_change ELSE 0 END), 0) AS inbound_qty,
                       COALESCE(SUM(CASE WHEN qty_change < 0 THEN -qty_change ELSE 0 END), 0) AS outbound_qty
                FROM stock_movements
                WHERE created_at >= date_trunc('month', CURRENT_DATE)
                """));
        return report;
    }

    private Map<String, Object> getMonthOverMonthFlow() {
        Map<String, Object> report = new LinkedHashMap<>();
        Map<String, Object> currentInbound = jdbcTemplate.queryForMap("""
                SELECT COUNT(DISTINCT ir.id) AS receipts,
                       COALESCE(SUM(iri.received_qty), 0) AS received_qty
                FROM inbound_receipts ir
                LEFT JOIN inbound_receipt_items iri ON iri.receipt_id = ir.id
                WHERE ir.received_date >= date_trunc('month', CURRENT_DATE)
                """);
        Map<String, Object> previousInbound = jdbcTemplate.queryForMap("""
                SELECT COUNT(DISTINCT ir.id) AS receipts,
                       COALESCE(SUM(iri.received_qty), 0) AS received_qty
                FROM inbound_receipts ir
                LEFT JOIN inbound_receipt_items iri ON iri.receipt_id = ir.id
                WHERE ir.received_date >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                  AND ir.received_date < date_trunc('month', CURRENT_DATE)
                """);
        Map<String, Object> currentOutbound = jdbcTemplate.queryForMap("""
                SELECT COUNT(DISTINCT so.id) AS sales_orders,
                       COALESCE(SUM(soi.ordered_qty), 0) AS ordered_qty,
                       COALESCE(SUM(soi.shipped_qty), 0) AS shipped_qty
                FROM sales_orders so
                LEFT JOIN sales_order_items soi ON soi.sales_order_id = so.id
                WHERE so.status <> 'CANCELLED'
                  AND so.created_at >= date_trunc('month', CURRENT_DATE)
                """);
        Map<String, Object> previousOutbound = jdbcTemplate.queryForMap("""
                SELECT COUNT(DISTINCT so.id) AS sales_orders,
                       COALESCE(SUM(soi.ordered_qty), 0) AS ordered_qty,
                       COALESCE(SUM(soi.shipped_qty), 0) AS shipped_qty
                FROM sales_orders so
                LEFT JOIN sales_order_items soi ON soi.sales_order_id = so.id
                WHERE so.status <> 'CANCELLED'
                  AND so.created_at >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                  AND so.created_at < date_trunc('month', CURRENT_DATE)
                """);
        report.put("current_inbound", currentInbound);
        report.put("previous_inbound", previousInbound);
        report.put("current_outbound", currentOutbound);
        report.put("previous_outbound", previousOutbound);
        report.put("delta", Map.of(
                "received_qty", numeric(currentInbound.get("received_qty")) - numeric(previousInbound.get("received_qty")),
                "ordered_qty", numeric(currentOutbound.get("ordered_qty")) - numeric(previousOutbound.get("ordered_qty")),
                "shipped_qty", numeric(currentOutbound.get("shipped_qty")) - numeric(previousOutbound.get("shipped_qty"))
        ));
        return report;
    }

    // Lấy tình trạng các dòng picking chưa hoàn tất.
    private List<Map<String, Object>> getPickingStatus(Map<String, Object> params) {
        String query = normalize(firstText(params, "query"));
        boolean pendingOnly = query.contains("pending") || query.contains("cho picking") || query.contains("cho pick")
                || query.contains("dang cho");
        String statusFilter = pendingOnly ? "pi.status = 'PENDING'" : "pi.status <> 'COMPLETED'";
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
                WHERE %s
                ORDER BY pi.status ASC, pi.pick_sequence ASC NULLS LAST
                LIMIT 50
                """.formatted(statusFilter));
    }

    // Lấy các cycle count đang mở.
    private List<Map<String, Object>> getActiveCycleCounts() {
        return jdbcTemplate.queryForList("""
                SELECT
                    cc.id AS cycle_count_id,
                    cc.status,
                    cc.description,
                    cc.scheduled_at,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name,
                    COUNT(cci.id) AS item_count
                FROM cycle_counts cc
                LEFT JOIN warehouses w ON w.id = cc.warehouse_id
                LEFT JOIN cycle_count_items cci ON cci.cycle_count_id = cc.id
                WHERE cc.status IN ('PENDING', 'IN_PROGRESS')
                GROUP BY cc.id, cc.status, cc.description, cc.scheduled_at, w.code, w.name
                ORDER BY cc.scheduled_at ASC NULLS LAST, cc.created_at DESC
                LIMIT 50
                """);
    }

    // Lấy các dòng kiểm kê đang lệch hoặc chưa đếm xong.
    private List<Map<String, Object>> getCycleCountVariance(Map<String, Object> params) {
        ResolvedWarehouse warehouse = resolveWarehouse(params);
        if (warehouse == null && hasWarehouseHint(params)) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
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
                """);
        if (warehouse != null) {
            sql.append(" AND LOWER(w.code) = LOWER(?)");
            args.add(warehouse.code());
        }
        sql.append("""
                ORDER BY ABS(COALESCE(cci.discrepancy, 0)) DESC, cc.created_at DESC
                LIMIT 50
                """);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> getPutawayByWarehouse() {
        return jdbcTemplate.queryForList("""
                SELECT
                    COALESCE(w.code, 'UNKNOWN') AS warehouse_code,
                    COALESCE(w.name, 'Không xác định') AS warehouse_name,
                    COUNT(*) AS task_count,
                    COALESCE(SUM(pt.qty_to_putaway), 0) AS qty_to_putaway
                FROM putaway_tasks pt
                LEFT JOIN locations l ON l.id = pt.suggested_location_id
                LEFT JOIN warehouses w ON w.id = l.warehouse_id
                WHERE pt.status IN ('PENDING', 'IN_PROGRESS', 'ASSIGNED')
                GROUP BY w.code, w.name
                ORDER BY task_count DESC, qty_to_putaway DESC
                LIMIT 10
                """);
    }

    // Lấy các yêu cầu trả hàng đang chờ xử lý.
    private List<Map<String, Object>> getPendingRma() {
        return jdbcTemplate.queryForList("""
                SELECT
                    r.rma_number,
                    r.customer_name,
                    r.status,
                    r.reason,
                    w.code AS warehouse_code,
                    w.name AS warehouse_name,
                    r.created_at
                FROM rma_headers r
                LEFT JOIN warehouses w ON w.id = r.warehouse_id
                WHERE r.status IN ('REQUESTED', 'RECEIVED')
                ORDER BY r.created_at ASC
                LIMIT 50
                """);
    }

    // Tổng hợp các việc vận hành cần chú ý trong ngày.
    private Map<String, Object> getDailyTasks() {
        Map<String, Object> tasks = new LinkedHashMap<>();
        tasks.put("pending_putaway", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total
                FROM putaway_tasks
                WHERE status IN ('PENDING', 'IN_PROGRESS', 'ASSIGNED')
                """));
        tasks.put("pending_picking", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total
                FROM picking_items
                WHERE status = 'PENDING'
                """));
        tasks.put("active_cycle_counts", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total
                FROM cycle_counts
                WHERE status IN ('PENDING', 'IN_PROGRESS')
                """));
        tasks.put("near_expiry_7_days", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total
                FROM stock_levels
                WHERE expiry_date IS NOT NULL
                  AND expiry_date <= ?
                  AND qty_on_hand > 0
                """, LocalDate.now().plusDays(7)));
        tasks.put("pending_rma", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total
                FROM rma_headers
                WHERE status IN ('REQUESTED', 'RECEIVED')
                """));
        return tasks;
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

    private Map<String, Object> getGlobalSearch(Map<String, Object> params) {
        String keyword = firstText(params, "query", "code", "sku", "product");
        Map<String, Object> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(keyword)) {
            result.put("message", "Thiếu từ khóa tìm kiếm.");
            return result;
        }
        String like = like(keyword);
        result.put("products", jdbcTemplate.queryForList("""
                SELECT sku AS code, name, status, 'PRODUCT' AS type
                FROM products
                WHERE LOWER(sku) LIKE ? OR LOWER(name) LIKE ? OR LOWER(barcode_ean13) LIKE ?
                ORDER BY updated_at DESC NULLS LAST
                LIMIT 5
                """, like, like, like));
        result.put("warehouses", jdbcTemplate.queryForList("""
                SELECT code, name, CASE WHEN is_active THEN 'ACTIVE' ELSE 'INACTIVE' END AS status, 'WAREHOUSE' AS type
                FROM warehouses
                WHERE LOWER(code) LIKE ? OR LOWER(name) LIKE ? OR LOWER(address) LIKE ?
                ORDER BY is_active DESC, name ASC
                LIMIT 5
                """, like, like, like));
        result.put("suppliers", jdbcTemplate.queryForList("""
                SELECT code, name, status, 'SUPPLIER' AS type
                FROM suppliers
                WHERE LOWER(code) LIKE ? OR LOWER(name) LIKE ? OR LOWER(contact_email) LIKE ?
                ORDER BY updated_at DESC NULLS LAST
                LIMIT 5
                """, like, like, like));
        result.put("customers", jdbcTemplate.queryForList("""
                SELECT code, name, CASE WHEN is_active THEN 'ACTIVE' ELSE 'INACTIVE' END AS status, 'CUSTOMER' AS type
                FROM customers
                WHERE LOWER(code) LIKE ? OR LOWER(name) LIKE ? OR LOWER(email) LIKE ?
                ORDER BY updated_at DESC NULLS LAST
                LIMIT 5
                """, like, like, like));
        result.put("purchase_orders", jdbcTemplate.queryForList("""
                SELECT po_number AS code, status, 'PURCHASE_ORDER' AS type
                FROM purchase_orders
                WHERE LOWER(po_number) LIKE ?
                ORDER BY created_at DESC
                LIMIT 5
                """, like));
        result.put("sales_orders", jdbcTemplate.queryForList("""
                SELECT so_number AS code, customer_name AS name, status, 'SALES_ORDER' AS type
                FROM sales_orders
                WHERE LOWER(so_number) LIKE ? OR LOWER(customer_name) LIKE ?
                ORDER BY created_at DESC
                LIMIT 5
                """, like, like));
        return result;
    }

    private List<Map<String, Object>> getAuditLogs(Map<String, Object> params) {
        String query = firstText(params, "query", "code");
        String dateRange = firstText(params, "dateRange");
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT service_name, module, action_type, action, entity_type, entity_name,
                       actor_name, actor_email, reason, created_at
                FROM audit_logs
                WHERE 1 = 1
                """);
        if (StringUtils.hasText(query)) {
            sql.append("""
                     AND (
                        LOWER(service_name) LIKE ? OR LOWER(module) LIKE ? OR LOWER(action) LIKE ?
                        OR LOWER(entity_type) LIKE ? OR LOWER(entity_name) LIKE ? OR LOWER(actor_name) LIKE ?
                     )
                    """);
            String like = like(query);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        appendDateRange(sql, args, "created_at", dateRange);
        sql.append(" ORDER BY created_at DESC LIMIT ").append(DEFAULT_LIMIT);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> getAiAuditLogs(Map<String, Object> params) {
        String query = firstText(params, "query");
        String dateRange = firstText(params, "dateRange");
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT session_id, question, generated_sql, rows_returned,
                       execution_error, latency_ms, created_at
                FROM ai_audit_logs
                WHERE 1 = 1
                """);
        if (StringUtils.hasText(query)) {
            sql.append(" AND LOWER(question) LIKE ?");
            args.add(like(query));
        }
        appendDateRange(sql, args, "created_at", dateRange);
        sql.append(" ORDER BY created_at DESC LIMIT ").append(DEFAULT_LIMIT);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    // Trả lời hướng dẫn thao tác theo câu hỏi cụ thể.
    private String getGuide(Map<String, Object> params) {
        String query = normalize(firstText(params, "query"));
        if (query.contains("xin chao") || query.contains("chao ban") || query.contains("hello")
                || query.contains("hi ") || query.contains("ban la ai") || query.contains("ban co the giup")
                || query.contains("ban lam duoc gi")) {
            return """
                    Xin chào, tôi là trợ lý AI vận hành kho StockMaster-WMS.
                    Tôi có thể giúp bạn tra cứu tồn kho, kho/vị trí, nhập kho, putaway, xuất kho, picking, kiểm kê, RMA và báo cáo vận hành. Bạn muốn kiểm tra nội dung nào?
                    """.trim();
        }
        if ((query.contains("tao") && query.contains("phieu nhap")) || query.contains("tao don nhap")) {
            return """
                    Để tạo phiếu nhập hoặc purchase order mới, bạn vào màn hình Đơn nhập/Purchase Orders, khai báo nhà cung cấp, kho nhận, ngày dự kiến và thêm các dòng hàng. Theo quyền hiện tại của hệ thống, thao tác tạo mới dành cho ADMIN hoặc WAREHOUSE_MANAGER.
                    """.trim();
        }
        if (query.contains("nhap") && (query.contains("lo hang") || query.contains("hang moi"))) {
            return """
                    Cách nhập lô hàng mới:
                    1. Tạo hoặc chọn Purchase Order đã được duyệt.
                    2. Vào màn hình nhận hàng, chọn PO và nhập số lượng thực nhận cho từng dòng.
                    3. Chọn kho/vị trí nhận tạm, nhập lot number và hạn dùng nếu sản phẩm có quản lý lô/hạn.
                    4. Lưu phiếu nhập. Sau khi nhận hàng, hệ thống sẽ có dữ liệu để tạo hoặc theo dõi Putaway Task.
                    """.trim();
        }
        if (query.contains("bien nhan") || query.contains("phieu nhap") || query.contains("receipt")) {
            return """
                    Cách tạo biên nhận nhập kho:
                    1. Chọn Purchase Order cần nhận hàng.
                    2. Tạo phiếu nhập, nhập ngày nhận, kho nhận và vị trí staging/receiving.
                    3. Khai báo số lượng thực nhận, lot number và hạn dùng nếu có.
                    4. Lưu phiếu để hệ thống cập nhật trạng thái nhận hàng và sinh dữ liệu putaway.
                    """.trim();
        }
        if ((query.contains("xem") || query.contains("kiem tra")) && query.contains("ton kho")) {
            return """
                    Để xem tồn kho hiện tại, bạn vào màn hình Tồn kho hoặc hỏi trực tiếp theo SKU/tên sản phẩm, mã kho hay vị trí. Nếu cần số liệu chính xác hơn, nên nêu rõ thêm kho hoặc khoảng thời gian.
                    """.trim();
        }
        if (query.contains("excel") || (query.contains("xuat") && query.contains("bao cao"))) {
            return """
                    Hệ thống có các màn hình hỗ trợ export Excel như sản phẩm và đơn xuất. Bạn mở đúng màn hình nghiệp vụ, áp dụng bộ lọc cần thiết rồi dùng chức năng Export/Xuất Excel nếu màn hình đó có hỗ trợ.
                    """.trim();
        }
        if (query.contains("quen mat khau") || query.contains("doi mat khau")) {
            return """
                    Nếu quên mật khẩu, bạn cần đi qua luồng đăng nhập/khôi phục tài khoản hoặc liên hệ ADMIN để được đặt lại. Trợ lý AI không thể xem hay cấp lại mật khẩu qua chat.
                    """.trim();
        }
        if ((query.contains("cho duyet") || query.contains("duyet")) && query.contains("phieu nhap")
                && (query.contains("bao gio") || query.contains("khi nao"))) {
            return """
                    Trợ lý AI không thể cam kết thời điểm duyệt phiếu nhập. Bạn nên kiểm tra trạng thái PO, người phụ trách duyệt và các điều kiện còn thiếu như dòng hàng, số lượng hoặc chứng từ trước khi liên hệ ADMIN/WAREHOUSE_MANAGER.
                    """.trim();
        }
        if ((query.contains("toi la staff") || query.contains("nhan vien kho"))
                && query.contains("duyet") && query.contains("phieu nhap")) {
            return """
                    Theo phân quyền hiện tại, WAREHOUSE_STAFF không có quyền duyệt phiếu nhập. Quyền duyệt đơn nhập thuộc ADMIN hoặc WAREHOUSE_MANAGER.
                    """.trim();
        }
        if ((query.contains("toi la manager") || query.contains("warehouse_manager") || query.contains("quan ly"))
                && query.contains("tao tai khoan")) {
            return """
                    Theo phân quyền hiện tại, việc tạo tài khoản mới thuộc API quản lý người dùng dành riêng cho ADMIN. WAREHOUSE_MANAGER không có quyền tạo người dùng mới.
                    """.trim();
        }
        if (query.contains("bao cao tai chinh") || query.contains("tai chinh")) {
            return """
                    Trợ lý AI hiện tập trung vào dữ liệu vận hành kho. Tôi chưa có tool riêng cho báo cáo tài chính trong hệ thống này, nên không nên trả số liệu tài chính qua chat nếu chưa có module hoặc API tương ứng.
                    """.trim();
        }
        if (query.contains("sua thong tin san pham")) {
            return """
                    Theo phân quyền hiện tại, cập nhật thông tin sản phẩm là thao tác dành cho ADMIN hoặc WAREHOUSE_MANAGER. WAREHOUSE_STAFF chỉ có quyền tra cứu và đọc dữ liệu sản phẩm.
                    """.trim();
        }
        if ((query.contains("ai") || query.contains("nguoi nao")) && query.contains("duyet")
                && query.contains("phieu nhap")) {
            return """
                    Tôi chưa có tool trực tiếp để trả người đã duyệt phiếu nhập từ cuộc chat này. Nếu cần truy vết, bạn nên kiểm tra audit log hoặc chi tiết nghiệp vụ của phiếu nhập bằng tài khoản có quyền phù hợp.
                    """.trim();
        }
        if (query.contains("putaway")) {
            return """
                    Cách tạo Putaway Task:
                    1. Hoàn tất phiếu nhập kho cho PO hoặc lô hàng cần cất.
                    2. Chọn dòng hàng cần putaway, số lượng cần cất và vị trí gợi ý nếu có.
                    3. Gán nhân viên thực hiện nếu quy trình yêu cầu.
                    4. Khi hàng được đưa vào vị trí thực tế, cập nhật task sang hoàn tất để tồn kho được phản ánh đúng vị trí.
                    """
                    .trim();
        }
        if (query.contains("picking")) {
            return """
                    Cách kiểm tra tình trạng picking:
                    1. Mở màn hình Picking hoặc Sales Order.
                    2. Tìm theo mã SO, ví dụ SO-XXXX.
                    3. Kiểm tra từng dòng: số lượng cần pick, đã pick, vị trí lấy hàng và trạng thái PENDING/PICKED.
                    4. Nếu còn PENDING, ưu tiên xử lý các đơn có priority cao hoặc hạn giao gần.
                    """.trim();
        }
        if (query.contains("cycle count") || query.contains("kiem ke")) {
            if (query.contains("chenh lech am") || query.contains("lech am")) {
                return """
                        Nếu phiếu kiểm kê lệch âm, bạn nên kiểm tra lại vị trí, lot, giao dịch xuất gần nhất và lịch sử điều chỉnh trước khi duyệt. Chỉ khi đã xác nhận nguyên nhân thực tế thì ADMIN hoặc WAREHOUSE_MANAGER mới nên phê duyệt và điều chỉnh tồn.
                        """.trim();
            }
            return """
                    Cách bắt đầu cycle count mới:
                    1. Chọn kho và phạm vi kiểm kê như vị trí, SKU hoặc zone.
                    2. Tạo lịch cycle count và gán người thực hiện.
                    3. Nhân viên ghi nhận số lượng đếm thực tế cho từng dòng.
                    4. Duyệt kết quả, xử lý chênh lệch và tạo điều chỉnh tồn nếu được phê duyệt.
                    """.trim();
        }
        if (query.contains("rma") || query.contains("tra ve") || query.contains("tra hang")) {
            return """
                    Quy trình xử lý RMA:
                    1. Tạo yêu cầu RMA từ đơn bán hoặc thông tin khách hàng.
                    2. Nhận hàng trả về tại kho và kiểm tra tình trạng.
                    3. Phân loại: nhập lại tồn tốt, cách ly hàng lỗi hoặc tạo xử lý hủy/sửa chữa.
                    4. Cập nhật trạng thái RMA đến khi hoàn tất.
                    """.trim();
        }
        if ((query.contains("100") || query.contains("xuat")) && query.contains("ton kho")
                && (query.contains("chi co") || query.contains("khong du") || query.contains("chi con"))) {
            return """
                    Nếu nhu cầu xuất vượt tồn khả dụng, bạn nên kiểm tra lại tồn khả dụng, lượng đang giữ chỗ và khả năng nhập bù trước. Không nên bỏ qua kiểm tra tồn; hãy tách đơn, giảm số lượng xuất hoặc chờ bổ sung hàng.
                    """.trim();
        }
        if (query.contains("duyet nham") && query.contains("phieu nhap")) {
            return """
                    Nếu đã duyệt nhầm phiếu nhập, hãy kiểm tra phiếu đó đang ở trạng thái nào. Theo API hiện tại, ADMIN hoặc WAREHOUSE_MANAGER có thể hủy PO khi trạng thái còn DRAFT, APPROVED hoặc PARTIAL; nếu đã phát sinh nhận hàng thì cần rà soát tác động trước khi hủy.
                    """.trim();
        }
        if (query.contains("bao loi") && query.contains("san pham") && query.contains("phieu nhap")) {
            return """
                    Khi thêm sản phẩm vào phiếu nhập bị lỗi, bạn nên kiểm tra quyền hiện tại, trạng thái PO, dữ liệu dòng hàng bắt buộc như SKU, số lượng, đơn giá và tính hợp lệ của sản phẩm. Nếu lỗi vẫn lặp lại, hãy kiểm tra log backend hoặc payload request tại màn hình PO/PO items.
                    """.trim();
        }
        if (query.contains("barcode")) {
            return """
                    Cách quét barcode khi nhập kho:
                    1. Mở màn hình nhận hàng hoặc nhập kho.
                    2. Đặt con trỏ vào ô SKU/barcode và quét mã bằng máy quét.
                    3. Kiểm tra sản phẩm được nhận diện, nhập số lượng thực nhận.
                    4. Bổ sung lot number/hạn dùng nếu sản phẩm yêu cầu theo dõi.
                    """.trim();
        }
        if (query.contains("dieu chinh ton kho") || query.contains("ton kho thu cong")) {
            return """
                    Cách điều chỉnh tồn kho thủ công:
                    1. Kiểm tra tồn hiện tại theo SKU, kho và vị trí.
                    2. Tạo phiếu điều chỉnh với lý do rõ ràng.
                    3. Nhập số lượng tăng/giảm và vị trí ảnh hưởng.
                    4. Chỉ xác nhận sau khi có quyền duyệt hoặc bằng chứng kiểm kê.
                    """.trim();
        }
        if (query.contains("hang hong") || query.contains("hang loi")) {
            return """
                    Quy trình xử lý hàng hỏng:
                    1. Chuyển hàng về khu cách ly hoặc vị trí hold.
                    2. Ghi nhận SKU, lot, số lượng và nguyên nhân hỏng.
                    3. Tạo điều chỉnh tồn hoặc RMA nội bộ theo quy định.
                    4. Lưu biên bản xử lý để đối soát báo cáo tồn kho.
                    """.trim();
        }
        if (query.contains("ton kho") && query.contains("vi tri")) {
            return """
                    Cách kiểm tra tồn kho theo vị trí:
                    1. Vào màn hình tồn kho hoặc vị trí kho.
                    2. Lọc theo mã kho, mã vị trí, zone/aisle/rack/bin hoặc SKU.
                    3. Kiểm tra các cột tồn hiện có, tồn đã giữ chỗ và tồn khả dụng.
                    4. Nếu cần chi tiết lô, mở dòng tồn để xem lot number và hạn dùng.
                    """.trim();
        }
        return """
                Tôi có thể hỗ trợ tra cứu kho, vị trí, tồn kho, hàng tồn thấp, hàng sắp hết hạn, putaway, đơn nhập, đơn xuất, picking, kiểm kê và tổng quan vận hành.
                Với yêu cầu cần số liệu, hãy nêu rõ mã SKU/tên sản phẩm, kho hoặc khoảng thời gian nếu có.
                """
                .trim();
    }

    private String getUnsupportedReply(Map<String, Object> params) {
        String query = normalize(firstText(params, "query"));
        if (query.contains("mat khau") || query.contains("password")) {
            return "Tôi không thể cung cấp, suy đoán hoặc khôi phục mật khẩu qua chat.";
        }
        if ((query.contains("xoa") || query.contains("delete") || query.contains("drop"))
                && (query.contains("tat ca") || query.contains("toan bo"))) {
            return "Tôi không thể hỗ trợ xóa hàng loạt dữ liệu hoặc hướng dẫn thao tác phá hủy qua chat.";
        }
        if (query.contains("bo qua") && query.contains("kiem tra ton kho")) {
            return "Không nên bỏ qua kiểm tra tồn kho khi xuất hàng. Bạn cần giữ bước kiểm tra tồn khả dụng để tránh âm tồn và sai lệch vận hành.";
        }
        if (query.contains("tu dong duyet") || (query.contains("duyet tat ca") && query.contains("cho"))) {
            return "Tôi không thể tự động duyệt hàng loạt chứng từ qua chat. Các thao tác duyệt cần được thực hiện trong luồng nghiệp vụ và theo đúng phân quyền.";
        }
        if (query.contains("sua so luong ton kho") || query.contains("thanh 99999")) {
            return "Tôi không thể hỗ trợ chỉnh sửa tùy tiện số lượng tồn kho. Nếu cần điều chỉnh, hãy dùng luồng inventory adjustment với lý do và quyền duyệt phù hợp.";
        }
        return """
                Tôi hiện chỉ hỗ trợ các câu hỏi liên quan đến vận hành kho như tồn kho, nhập/xuất, putaway, picking, kiểm kê, RMA và báo cáo vận hành. Tôi không thực hiện thao tác phá hủy, vượt quyền hoặc truy xuất bí mật qua chat.
                """.trim();
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

    private boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private boolean hasAnyAuthority(String... authorities) {
        for (String authority : authorities) {
            if (hasAuthority(authority)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIntentAllowed(AiIntent intent) {
        if (intent == null) {
            return false;
        }
        if (intent == AiIntent.AUDIT_LOG || intent == AiIntent.AI_AUDIT_LOG) {
            return hasAuthority(ADMIN);
        }
        if (isPublicAiIntent(intent)) {
            return hasAnyAuthority(ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF, REPORT_VIEWER);
        }
        if (isReportViewerIntent(intent)) {
            return hasAnyAuthority(ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF, REPORT_VIEWER);
        }
        return hasAnyAuthority(ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF);
    }

    private static boolean isPublicAiIntent(AiIntent intent) {
        return switch (intent) {
            case GENERAL_GUIDE, AMBIGUOUS, UNSUPPORTED -> true;
            default -> false;
        };
    }

    private static boolean isReportViewerIntent(AiIntent intent) {
        return switch (intent) {
            case WAREHOUSE_COUNT,
                    STOCK_TOTAL,
                    INVENTORY_VALUE,
                    FLOW_REPORT,
                    DAILY_TASKS,
                    REPORT_SUMMARY,
                    INBOUND_REPORT,
                    OUTBOUND_REPORT,
                    MONTHLY_REPORT,
                    MONTH_OVER_MONTH_FLOW -> true;
            default -> false;
        };
    }

    private boolean countOnly(Map<String, Object> params) {
        Object value = params == null ? null : params.get("countOnly");
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private double numeric(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private void appendDateRange(StringBuilder sql, List<Object> args, String column, String dateRange) {
        if (!StringUtils.hasText(dateRange)) {
            return;
        }
        String normalized = dateRange.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "TODAY" -> sql.append(" AND ").append(column).append(" >= CURRENT_DATE");
            case "THIS_WEEK" -> sql.append(" AND ").append(column).append(" >= date_trunc('week', CURRENT_DATE)");
            case "LAST_7_DAYS" -> sql.append(" AND ").append(column).append(" >= CURRENT_DATE - INTERVAL '7 days'");
            case "THIS_MONTH" -> sql.append(" AND ").append(column).append(" >= date_trunc('month', CURRENT_DATE)");
            default -> {
                // Không thêm điều kiện nếu dateRange không thuộc nhóm được hỗ trợ.
            }
        }
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

    private String extractFirstNumber(String text) {
        if (text == null) {
            return null;
        }
        var matcher = Pattern.compile("\\d+").matcher(text);
        return matcher.find() ? matcher.group() : null;
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
        String directSku = resolveProductSkuByKeyword(normalizedQuery);
        if (StringUtils.hasText(directSku)) {
            return directSku;
        }
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
        int threshold = normalizedQuery.contains("iphone") || normalizedQuery.contains("dell")
                || normalizedQuery.contains("laptop") ? 1 : 2;
        return bestScore >= threshold ? bestSku : null;
    }

    private String resolveProductSkuByKeyword(String normalizedQuery) {
        String keyword = null;
        if (normalizedQuery.contains("iphone")) {
            keyword = "iphone";
        } else if (normalizedQuery.contains("xps")) {
            keyword = "xps";
        } else if (normalizedQuery.contains("dell")) {
            keyword = "dell";
        } else if (normalizedQuery.contains("laptop")) {
            keyword = "laptop";
        }
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                SELECT sku, name
                FROM products
                WHERE status = 'ACTIVE'
                  AND LOWER(name) LIKE ?
                ORDER BY name ASC
                LIMIT 20
                """, like(keyword));
        for (Map<String, Object> candidate : candidates) {
            String name = text(candidate.get("name"));
            if (numbersCompatible(normalizedQuery, normalize(name))) {
                return text(candidate.get("sku"));
            }
        }
        return null;
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
        ResolvedWarehouse alias = resolveWarehouseAlias(normalizedSearch);
        if (alias != null) {
            return alias;
        }
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

    private ResolvedWarehouse resolveWarehouseAlias(String normalizedSearch) {
        if (normalizedSearch == null) {
            return null;
        }
        String code = null;
        if (normalizedSearch.contains("wh-hcm") || normalizedSearch.contains("kho hcm")
                || normalizedSearch.contains("tp hcm") || normalizedSearch.contains("tphcm")
                || normalizedSearch.contains("tp.hcm")) {
            code = "WH-HCM";
        } else if (normalizedSearch.contains("wh-hn") || normalizedSearch.contains("kho hn")
                || normalizedSearch.contains("ha noi") || normalizedSearch.contains("hanoi")) {
            code = "WH-HN";
        } else if (normalizedSearch.contains("da nang") || normalizedSearch.contains("danang")) {
            return null;
        }
        if (!StringUtils.hasText(code)) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT code, name, is_active
                FROM warehouses
                WHERE LOWER(code) = LOWER(?)
                LIMIT 1
                """, code);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new ResolvedWarehouse(text(row.get("code")), text(row.get("name")),
                Boolean.TRUE.equals(row.get("is_active")));
    }

    // Kiểm tra câu hỏi có nhắc đến một kho cụ thể hay không.
    private boolean hasWarehouseHint(Map<String, Object> params) {
        if (StringUtils.hasText(firstText(params, "warehouseCode", "warehouse"))) {
            return true;
        }
        String normalized = normalize(firstText(params, "query"));
        return normalized.contains(" o kho ") || normalized.contains(" tai kho ") || normalized.contains(" trong kho ")
                || normalized.contains(" kho wh-") || normalized.contains("kho hcm") || normalized.contains("kho hn")
                || normalized.contains("kho ha noi") || normalized.contains("kho da nang")
                || normalized.contains("tp hcm") || normalized.contains("wh-hcm") || normalized.contains("wh-hn");
    }

    private boolean asksByWarehouse(String query) {
        String normalized = normalize(query);
        return normalized.contains("tung kho")
                || normalized.contains("moi kho")
                || normalized.contains("theo kho")
                || normalized.contains("per warehouse")
                || normalized.contains("by warehouse");
    }

    // Chấm điểm candidate theo token xuất hiện trong câu hỏi.
    private int scoreCandidate(String normalizedQuery, String... values) {
        int score = 0;
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String normalizedValue = normalize(value);
            if (normalizedQuery.contains("xps") && !normalizedValue.contains("xps")) {
                continue;
            }
            if (!numbersCompatible(normalizedQuery, normalizedValue)) {
                continue;
            }
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

    // Tránh match nhầm model sản phẩm khác đời, ví dụ hỏi iPhone 16 nhưng chỉ có
    // iPhone 15.
    private boolean numbersCompatible(String normalizedQuery, String normalizedValue) {
        List<String> queryNumbers = Pattern.compile("\\d+")
                .matcher(normalizedQuery == null ? "" : normalizedQuery)
                .results()
                .map(MatchResult::group)
                .toList();
        if (queryNumbers.isEmpty()) {
            return true;
        }
        List<String> valueNumbers = Pattern.compile("\\d+")
                .matcher(normalizedValue == null ? "" : normalizedValue)
                .results()
                .map(MatchResult::group)
                .toList();
        return valueNumbers.containsAll(queryNumbers);
    }

    // Bỏ các từ chung để tránh match sai khi resolve entity.
    private boolean isMeaningfulToken(String token) {
        return StringUtils.hasText(token)
                && token.length() >= 2
                && !List.of("kho", "ton", "hang", "san", "pham", "con", "bao", "nhieu", "tai", "the",
                        "nao", "stock", "qty", "available", "reserved", "dc01", "ha", "noi", "tp", "hcm",
                        "co", "khong", "roi").contains(token);
    }

    // Chuẩn hóa text về không dấu và chữ thường.
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
                .replaceAll("\\biphon\\b", "iphone")
                .replaceAll("\\blap\\s+top\\b", "laptop")
                .replaceAll("\\bbn\\b", "bao nhieu")
                .replaceAll("\\bko\\b", "khong")
                .replaceAll("\\bk\\b", "khong");
    }

    private record ResolvedWarehouse(String code, String name, boolean active) {
    }
}
