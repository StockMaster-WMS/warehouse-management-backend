package com.ai_service.service;

import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiIntentRouterServiceTest {

    private final AiIntentRouterService router = new AiIntentRouterService(null, new ObjectMapper());

    @Test
    void routesSalesOrderStatusWithoutDashCode() {
        AiIntentResult result = router.route("Xem trạng thái SO SO99408", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.SALES_ORDER_STATUS);
        assertThat(result.safeParameters()).containsEntry("code", "SO99408");
        assertThat(result.safeParameters()).containsEntry("soId", "SO99408");
    }

    @Test
    void routesSalesOrderDetailWithoutDashCode() {
        AiIntentResult result = router.route("Đọc chi tiết đơn bán SO77437", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.SALES_ORDER_DETAIL);
        assertThat(result.safeParameters()).containsEntry("code", "SO77437");
    }

    @Test
    void routesPurchaseOrderLineItemsWithoutDashCode() {
        AiIntentResult result = router.route("Line items của PO PO22779?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.PURCHASE_ORDER_DETAIL);
        assertThat(result.safeParameters()).containsEntry("code", "PO22779");
        assertThat(result.safeParameters()).containsEntry("poId", "PO22779");
    }

    @Test
    void routesPurchaseOrderStatusWithoutDashCode() {
        AiIntentResult result = router.route("Status PO PO79609?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.PURCHASE_ORDER_STATUS);
        assertThat(result.safeParameters()).containsEntry("code", "PO79609");
    }

    @Test
    void routesCustomerSearchWithVietnameseAbbreviationAndQuotedKeyword() {
        AiIntentResult result = router.route("Tìm KH theo từ khóa 'Xanh'", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.CUSTOMER_SEARCH);
        assertThat(result.safeParameters()).containsEntry("keyword", "Xanh");
    }

    @Test
    void routesInventoryAndOutboundQuestionAsAmbiguous() {
        AiIntentResult result = router.route("Cho tôi tồn kho và đơn xuất ưu tiên", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.AMBIGUOUS);
    }

    @Test
    void reusesHistoryForFollowUpReference() {
        AiIntentResult result = router.route("Kho đó ở đâu?", List.of(
                Map.of("role", "user", "content", "Thông tin kho WH-HCM-DC01"),
                Map.of("role", "assistant", "content", "WH-HCM-DC01 - Kho Tổng HCM")
        ));

        assertThat(result.getIntent()).isEqualTo(AiIntent.WAREHOUSE_DETAIL);
        assertThat(result.safeParameters()).containsEntry("warehouseCode", "WH-HCM-DC01");
    }

    @Test
    void routesPendingApprovalPurchaseOrdersCount() {
        AiIntentResult result = router.route("Hôm nay có bao nhiêu phiếu nhập đang chờ duyệt?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.PURCHASE_ORDER_STATUS);
        assertThat(result.safeParameters()).containsEntry("status", "DRAFT");
        assertThat(result.safeParameters()).containsEntry("dateRange", "TODAY");
        assertThat(result.safeParameters()).containsEntry("countOnly", true);
    }

    @Test
    void routesLatestApprovedOutbound() {
        AiIntentResult result = router.route("Phiếu xuất kho gần đây nhất được duyệt là phiếu nào?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.SALES_ORDER_STATUS);
        assertThat(result.safeParameters()).containsEntry("status", "APPROVED");
        assertThat(result.safeParameters()).containsEntry("latestOnly", true);
    }

    @Test
    void routesPasswordQuestionAsGuide() {
        AiIntentResult result = router.route("Tôi quên mật khẩu thì phải làm sao?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.GENERAL_GUIDE);
    }

    @Test
    void routesSupplierTopThisMonth() {
        AiIntentResult result = router.route("Nhà cung cấp nào có nhiều phiếu nhập nhất trong tháng này?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.SUPPLIER_TOP);
        assertThat(result.safeParameters()).containsEntry("dateRange", "THIS_MONTH");
    }

    @Test
    void routesInventoryValueQuestion() {
        AiIntentResult result = router.route("Tổng giá trị hàng tồn kho hiện tại là bao nhiêu?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.INVENTORY_VALUE);
    }

    @Test
    void routesMonthOverMonthFlowQuestion() {
        AiIntentResult result = router.route("So sánh số lượng nhập và xuất của tháng này với tháng trước?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.MONTH_OVER_MONTH_FLOW);
    }

    @Test
    void routesPurchaseOrderApprovalAuditQuestion() {
        AiIntentResult result = router.route("Ai là người đã duyệt phiếu nhập PO-2026-0001?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.PURCHASE_ORDER_APPROVAL_AUDIT);
        assertThat(result.safeParameters()).containsEntry("code", "PO-2026-0001");
    }

    @Test
    void routesNumericSkuStockQuestions() {
        AiIntentResult result = router.route("SKU 00018 ở WH-002 còn bao nhiêu?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.STOCK_BY_PRODUCT);
        assertThat(result.safeParameters()).containsEntry("sku", "00018");
        assertThat(result.safeParameters()).containsEntry("warehouseCode", "WH-002");
    }

    @Test
    void routesProductNameWithNumericSkuWarehouseQuestion() {
        AiIntentResult result = router.route("Rovi Bút bi xanh loại cao cấp 00018 ở kho nào?", List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.STOCK_BY_PRODUCT);
        assertThat(result.safeParameters()).containsEntry("sku", "00018");
    }

    @Test
    void routesSalesOrderPickingOperationalQuestions() {
        AiIntentResult assignee = router.route("Ai đang pick SO-2026-001?", List.of());
        AiIntentResult missing = router.route("SO-2026-001 còn thiếu gì?", List.of());

        assertThat(assignee.getIntent()).isEqualTo(AiIntent.PICKING_STATUS);
        assertThat(assignee.safeParameters()).containsEntry("soId", "SO-2026-001");
        assertThat(missing.getIntent()).isEqualTo(AiIntent.SALES_ORDER_DETAIL);
    }

    @Test
    void routesReceiptAndTaskCodes() {
        assertThat(router.route("Phiếu GR-2026-015 đã nhập xong chưa?", List.of()).getIntent())
                .isEqualTo(AiIntent.INBOUND_RECEIPT_STATUS);
        assertThat(router.route("GR-2026-015 để hàng ở đâu?", List.of()).getIntent())
                .isEqualTo(AiIntent.INBOUND_RECEIPT_DETAIL);
        assertThat(router.route("Putaway task PT-2026-003 xong chưa?", List.of()).getIntent())
                .isEqualTo(AiIntent.PENDING_PUTAWAY);
        assertThat(router.route("Picking task PK-2026-009 còn bao nhiêu món chưa lấy?", List.of()).getIntent())
                .isEqualTo(AiIntent.PICKING_STATUS);
    }

    @Test
    void routesCycleReturnAnalyticsAndPermissionQuestions() {
        assertThat(router.route("Session SC-2026-005 đã khóa chưa?", List.of()).getIntent())
                .isEqualTo(AiIntent.CYCLE_COUNT_STATUS);
        assertThat(router.route("SC-2026-005 có lệch SKU 00018 không?", List.of()).getIntent())
                .isEqualTo(AiIntent.CYCLE_COUNT_VARIANCE);
        assertThat(router.route("Return RT-2026-002 xử lý tới đâu rồi?", List.of()).getIntent())
                .isEqualTo(AiIntent.RMA_PENDING);
        assertThat(router.route("Sản phẩm nào quay vòng chậm nhất?", List.of()).getIntent())
                .isEqualTo(AiIntent.SLOW_MOVING_STOCK);
        assertThat(router.route("Tôi có quyền chỉnh tồn SKU 00018 không?", List.of()).getIntent())
                .isEqualTo(AiIntent.USER_PERMISSION);
    }
}
