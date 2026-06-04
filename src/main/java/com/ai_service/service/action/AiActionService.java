package com.ai_service.service.action;

import com.ai_service.dto.AiActionRequest;
import com.ai_service.dto.AiActionResponse;
import com.ai_service.dto.AiActionResponse.AiActionCandidate;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.entity.Product;
import com.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiActionService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final String ACTION_MARK_OUT_OF_STOCK = "MARK_PRODUCTS_OUT_OF_STOCK";

    private final ProductRepository productRepository;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public AiActionResponse preview(AiActionRequest request) {
        ActionPlan plan = buildPlan(request);
        List<AiActionCandidate> candidates = resolveCandidates(plan);
        int eligible = (int) candidates.stream().filter(AiActionCandidate::eligible).count();
        return new AiActionResponse(
                plan.actionType(),
                "PREVIEW",
                previewSummary(candidates.size(), eligible, plan.targetStatus()),
                true,
                plan.targetStatus(),
                candidates.size(),
                eligible,
                0,
                candidates.size() - eligible,
                candidates,
                warnings(candidates),
                Map.of(
                        "source", plan.source(),
                        "reason", plan.reason(),
                        "confirmEndpoint", "/api/v1/ai/actions/confirm"
                )
        );
    }

    @Transactional
    public AiActionResponse confirm(AiActionRequest request, Authentication authentication) {
        requireActionAuthority(authentication);
        ActionPlan plan = buildPlan(request);
        List<AiActionCandidate> candidates = resolveCandidates(plan);
        Set<String> eligibleSkus = candidates.stream()
                .filter(AiActionCandidate::eligible)
                .map(AiActionCandidate::sku)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (eligibleSkus.isEmpty()) {
            return new AiActionResponse(
                    plan.actionType(),
                    "NO_CHANGE",
                    "Không có sản phẩm đủ điều kiện để cập nhật.",
                    false,
                    plan.targetStatus(),
                    candidates.size(),
                    0,
                    0,
                    candidates.size(),
                    candidates,
                    warnings(candidates),
                    Map.of("source", plan.source(), "reason", plan.reason())
            );
        }

        List<Product> products = productRepository.findBySkuIn(eligibleSkus);
        int updated = 0;
        for (Product product : products) {
            if (!eligibleSkus.contains(product.getSku())) {
                continue;
            }
            String beforeStatus = product.getStatus();
            if (plan.targetStatus().equalsIgnoreCase(beforeStatus)) {
                continue;
            }
            Map<String, Object> before = productSnapshot(product);
            product.setStatus(plan.targetStatus());
            Product saved = productRepository.save(product);
            Map<String, Object> after = productSnapshot(saved);
            auditLogService.record("PRODUCT", "AI_STATUS_UPDATE",
                    "AI cập nhật trạng thái sản phẩm sau xác nhận",
                    "PRODUCT", saved.getId(), saved.getSku(), before, after,
                    plan.reason(), Map.of(
                            "aiActionType", plan.actionType(),
                            "source", plan.source(),
                            "previousStatus", beforeStatus,
                            "targetStatus", plan.targetStatus()
                    ));
            updated++;
        }

        List<AiActionCandidate> afterCandidates = resolveCandidates(plan);
        int skipped = Math.max(candidates.size() - updated, 0);
        return new AiActionResponse(
                plan.actionType(),
                "COMPLETED",
                "Đã cập nhật " + updated + " sản phẩm sang trạng thái " + statusLabel(plan.targetStatus()) + ".",
                false,
                plan.targetStatus(),
                candidates.size(),
                eligibleSkus.size(),
                updated,
                skipped,
                afterCandidates,
                warnings(afterCandidates),
                Map.of("source", plan.source(), "reason", plan.reason())
        );
    }

    private ActionPlan buildPlan(AiActionRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu thông tin action AI");
        }
        String actionType = normalizeActionType(request.getActionType());
        if (!ACTION_MARK_OUT_OF_STOCK.equals(actionType)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Action AI chưa được hỗ trợ: " + request.getActionType());
        }
        String targetStatus = StringUtils.hasText(request.getTargetStatus())
                ? request.getTargetStatus().trim().toUpperCase(Locale.ROOT)
                : "OUT_OF_STOCK";
        if (!Set.of("OUT_OF_STOCK", "INACTIVE").contains(targetStatus)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái đích không hợp lệ cho action này");
        }
        int limit = request.getLimit() == null ? DEFAULT_LIMIT : Math.max(1, Math.min(request.getLimit(), MAX_LIMIT));
        List<String> skuList = normalizeSkus(request.getSkuList());
        String source = StringUtils.hasText(request.getSource()) ? request.getSource().trim() : "USER_REQUEST";
        String reason = StringUtils.hasText(request.getReason())
                ? request.getReason().trim()
                : "AI đề xuất ngừng bán sản phẩm hết hàng/tồn thấp sau khi người dùng xác nhận";
        return new ActionPlan(actionType, source, skuList, targetStatus, reason, limit);
    }

    private List<AiActionCandidate> resolveCandidates(ActionPlan plan) {
        List<Map<String, Object>> rows = plan.skuList().isEmpty()
                ? lowStockRows(plan.limit())
                : rowsBySku(plan.skuList());
        return rows.stream()
                .map(row -> toCandidate(row, plan.targetStatus()))
                .sorted(Comparator.comparing(AiActionCandidate::eligible).reversed()
                        .thenComparing(AiActionCandidate::sku))
                .toList();
    }

    private List<Map<String, Object>> lowStockRows(int limit) {
        return namedJdbcTemplate.queryForList("""
                SELECT
                    p.id,
                    p.sku,
                    p.name AS product_name,
                    p.status,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available,
                    p.min_stock_qty
                FROM products p
                LEFT JOIN stock_levels sl ON sl.product_id = p.id
                WHERE p.status = 'ACTIVE'
                GROUP BY p.id, p.sku, p.name, p.status, p.min_stock_qty
                HAVING COALESCE(SUM(sl.qty_available), 0) < COALESCE(p.min_stock_qty, 0)
                ORDER BY qty_available ASC, p.name ASC
                LIMIT :limit
                """, new MapSqlParameterSource("limit", limit));
    }

    private List<Map<String, Object>> rowsBySku(List<String> skus) {
        if (skus.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("skus", skus);
        List<Map<String, Object>> rows = namedJdbcTemplate.queryForList("""
                SELECT
                    p.id,
                    p.sku,
                    p.name AS product_name,
                    p.status,
                    COALESCE(SUM(sl.qty_available), 0) AS qty_available,
                    p.min_stock_qty
                FROM products p
                LEFT JOIN stock_levels sl ON sl.product_id = p.id
                WHERE p.sku IN (:skus)
                GROUP BY p.id, p.sku, p.name, p.status, p.min_stock_qty
                """, params);
        Map<String, Map<String, Object>> bySku = rows.stream()
                .collect(Collectors.toMap(row -> stringValue(row.get("sku")), Function.identity(), (a, b) -> a));
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String sku : skus) {
            Map<String, Object> row = bySku.get(sku);
            if (row == null) {
                Map<String, Object> missing = new LinkedHashMap<>();
                missing.put("sku", sku);
                missing.put("product_name", "Không tìm thấy sản phẩm");
                missing.put("status", "NOT_FOUND");
                missing.put("qty_available", 0);
                missing.put("min_stock_qty", 0);
                ordered.add(missing);
            } else {
                ordered.add(row);
            }
        }
        return ordered;
    }

    private AiActionCandidate toCandidate(Map<String, Object> row, String targetStatus) {
        String sku = stringValue(row.get("sku"));
        String productName = stringValue(row.get("product_name"));
        String currentStatus = stringValue(row.get("status"));
        long qtyAvailable = longValue(row.get("qty_available"));
        Integer minStockQty = integerValue(row.get("min_stock_qty"));
        boolean found = !"NOT_FOUND".equalsIgnoreCase(currentStatus);
        boolean statusCanChange = found && !targetStatus.equalsIgnoreCase(currentStatus);
        boolean outOfStockSafe = !"OUT_OF_STOCK".equals(targetStatus) || qtyAvailable <= 0;
        boolean eligible = statusCanChange && outOfStockSafe;
        String reason;
        if (!found) {
            reason = "Không tìm thấy SKU trong danh mục sản phẩm.";
        } else if (!statusCanChange) {
            reason = "Sản phẩm đã ở trạng thái " + statusLabel(targetStatus) + ".";
        } else if (!outOfStockSafe) {
            reason = "Sản phẩm vẫn còn tồn khả dụng, cần kiểm tra trước khi ngừng bán.";
        } else {
            reason = "Đủ điều kiện cập nhật vì tồn khả dụng bằng 0 hoặc dưới ngưỡng an toàn.";
        }
        return new AiActionCandidate(sku, productName, currentStatus, targetStatus,
                qtyAvailable, minStockQty, eligible, reason);
    }

    private void requireActionAuthority(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xác nhận thao tác AI");
        }
        boolean allowed = authentication.getAuthorities().stream()
                .anyMatch(authority -> {
                    String value = authority.getAuthority();
                    return "ADMIN".equals(value) || "WAREHOUSE_MANAGER".equals(value);
                });
        if (!allowed) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Chỉ ADMIN hoặc WAREHOUSE_MANAGER được xác nhận thao tác cập nhật dữ liệu");
        }
    }

    private String normalizeActionType(String actionType) {
        if (!StringUtils.hasText(actionType)) {
            return ACTION_MARK_OUT_OF_STOCK;
        }
        String normalized = actionType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MARK_PRODUCTS_INACTIVE", "STOP_SELLING_PRODUCTS" -> ACTION_MARK_OUT_OF_STOCK;
            default -> normalized;
        };
    }

    private List<String> normalizeSkus(Collection<String> skus) {
        if (skus == null) {
            return List.of();
        }
        return skus.stream()
                .filter(StringUtils::hasText)
                .map(sku -> sku.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .limit(MAX_LIMIT)
                .toList();
    }

    private String previewSummary(int candidateCount, int eligibleCount, String targetStatus) {
        if (candidateCount == 0) {
            return "Không tìm thấy sản phẩm phù hợp để cập nhật.";
        }
        return "Tôi tìm thấy " + candidateCount + " sản phẩm, trong đó " + eligibleCount
                + " sản phẩm đủ điều kiện chuyển sang trạng thái " + statusLabel(targetStatus)
                + ". Vui lòng kiểm tra danh sách trước khi xác nhận.";
    }

    private List<String> warnings(List<AiActionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        long skipped = candidates.stream().filter(candidate -> !candidate.eligible()).count();
        if (skipped > 0) {
            warnings.add("Có " + skipped + " sản phẩm không đủ điều kiện và sẽ bị bỏ qua khi xác nhận.");
        }
        boolean hasAvailableStock = candidates.stream()
                .anyMatch(candidate -> candidate.qtyAvailable() > 0 && !candidate.eligible());
        if (hasAvailableStock) {
            warnings.add("Một số sản phẩm vẫn còn tồn khả dụng, hệ thống không tự ngừng bán các sản phẩm này.");
        }
        return warnings;
    }

    private String statusLabel(String status) {
        if (status == null || status.isBlank()) {
            return "không xác định";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> "đang bán/đang hoạt động";
            case "INACTIVE" -> "ngừng sử dụng";
            case "OUT_OF_STOCK" -> "tạm ngừng bán do hết hàng";
            case "NOT_FOUND" -> "không tìm thấy";
            default -> status;
        };
    }

    private Map<String, Object> productSnapshot(Product product) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", product.getId());
        snapshot.put("sku", product.getSku());
        snapshot.put("name", product.getName());
        snapshot.put("status", product.getStatus());
        return snapshot;
    }

    private String stringValue(Object value) {
        return Objects.toString(value, "");
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ActionPlan(
            String actionType,
            String source,
            List<String> skuList,
            String targetStatus,
            String reason,
            int limit
    ) {
    }
}
