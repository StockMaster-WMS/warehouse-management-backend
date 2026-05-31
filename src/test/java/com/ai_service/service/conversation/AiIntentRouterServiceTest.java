package com.ai_service.service.conversation;

import com.ai_service.client.AiTextClient;
import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    void doesNotReusePreviousSkuForGlobalInventoryQuestions() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "Sản phẩm Meko Máy tính bảng phiên bản mới 00013 còn tồn bao nhiêu?"),
                Map.of("role", "assistant", "content", "SKU ELEC-00013 hiện còn 0 đơn vị.")
        );

        AiIntentResult inventoryValue = router.route("Tổng giá trị tồn kho hiện tại khoảng bao nhiêu?", history);
        AiIntentResult highestStock = router.route("Sản phẩm nào tồn kho nhiều nhất hiện nay?", history);

        assertThat(inventoryValue.getIntent()).isEqualTo(AiIntent.INVENTORY_VALUE);
        assertThat(inventoryValue.safeParameters()).doesNotContainKey("sku");
        assertThat(highestStock.getIntent()).isEqualTo(AiIntent.STOCK_HIGHEST);
        assertThat(highestStock.safeParameters()).doesNotContainKey("sku");
    }

    @Test
    void removesStaleModelSkuWhenQuestionDoesNotReferenceHistory() {
        AiTextClient staleModel = new AiTextClient() {
            @Override
            public String generateIntent(String prompt) {
                return """
                        {"intent":"INVENTORY_VALUE","parameters":{"sku":"ELEC-00013","query":"Tổng giá trị hàng trong kho hiện nay?"},"confidence":0.88,"reason":"model stale context"}
                        """;
            }

            @Override
            public String generateAnswer(String prompt) {
                return "";
            }

            @Override
            public void generateAnswerStream(String prompt, Consumer<String> consumer, Supplier<Boolean> isCancelled) {
            }
        };
        AiIntentRouterService modelRouter = new AiIntentRouterService(staleModel, new ObjectMapper());

        AiIntentResult result = modelRouter.route("Tổng giá trị hàng trong kho hiện nay?", List.of(
                Map.of("role", "user", "content", "Sản phẩm Meko Máy tính bảng phiên bản mới 00013 còn tồn bao nhiêu?"),
                Map.of("role", "assistant", "content", "SKU ELEC-00013 hiện còn 0 đơn vị.")
        ));

        assertThat(result.getIntent()).isEqualTo(AiIntent.INVENTORY_VALUE);
        assertThat(result.safeParameters()).doesNotContainKey("sku");
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

    @ParameterizedTest
    @ValueSource(strings = {
            "có sản phẩm Xoài cát trong kho không",
            "có sản phẩm Bàn phím cơ trong kho không",
            "có mặt hàng Sữa tươi ở kho không"
    })
    void routesNaturalProductAvailabilityQuestionToStockLookup(String question) {
        AiIntentResult result = router.route(question, List.of());

        assertThat(result.getIntent()).isEqualTo(AiIntent.STOCK_BY_PRODUCT);
        assertThat(result.safeParameters()).containsEntry("query", question);
    }

    @ParameterizedTest
    @CsvSource({
            "'Xoài cát còn hàng không?', STOCK_BY_PRODUCT",
            "'Bàn phím cơ còn bao nhiêu cái?', STOCK_BY_PRODUCT",
            "'Sản phẩm iPhone còn ở kho nào?', STOCK_BY_PRODUCT",
            "'Hàng nào sắp hết trong kho?', LOW_STOCK",
            "'Mặt hàng nào còn nhiều nhất hiện tại?', STOCK_HIGHEST",
            "'Hôm nay có hàng nào mới nhập không?', INBOUND_TODAY",
            "'Có phiếu nhập nào đang chờ nhận hàng không?', PENDING_PO_RECEIPT",
            "'Phiếu nhập gần đây nhất là của nhà cung cấp nào?', LATEST_INBOUND",
            "'Có đơn nào đang bị trễ không?', OUTBOUND_DELAYED",
            "'Đơn nào còn thiếu hàng để giao?', OUTBOUND_SHORTAGE",
            "'Ai đang có nhiều việc picking nhất?', PICKING_PRODUCTIVITY",
            "'Hôm nay tôi cần làm những việc gì?', MY_TASKS",
            "'Có task putaway nào đang chờ lâu không?', PENDING_PUTAWAY",
            "'Tình hình kho hôm nay có gì cần chú ý?', REPORT_SUMMARY"
    })
    void routesNaturalWarehouseOperationsQuestions(String question, AiIntent expectedIntent) {
        AiIntentResult result = router.route(question, List.of());

        assertThat(result.getIntent()).isEqualTo(expectedIntent);
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

    @Test
    void toleratesCommonWarehouseCodeTypos() {
        AiIntentResult missingDash = router.route("SKU 00018 ở WH002 còn bao nhiêu?", List.of());
        AiIntentResult letterO = router.route("WH-OO2 còn SKU 00018 không?", List.of());

        assertThat(missingDash.getIntent()).isEqualTo(AiIntent.STOCK_BY_PRODUCT);
        assertThat(missingDash.safeParameters()).containsEntry("warehouseCode", "WH-002");
        assertThat(letterO.getIntent()).isEqualTo(AiIntent.STOCK_BY_PRODUCT);
        assertThat(letterO.safeParameters()).containsEntry("warehouseCode", "WH-002");
    }

    @Test
    void toleratesCommonBusinessCodeTyposAndMissingSeparators() {
        AiIntentResult salesOrder = router.route("S0 2026 001 đang trạng thái gì?", List.of());
        AiIntentResult receipt = router.route("GR2026015 de hang o dau?", List.of());
        AiIntentResult picking = router.route("PK 2026 009 uu tien khong?", List.of());

        assertThat(salesOrder.getIntent()).isEqualTo(AiIntent.SALES_ORDER_STATUS);
        assertThat(salesOrder.safeParameters()).containsEntry("code", "SO-2026-001");
        assertThat(receipt.getIntent()).isEqualTo(AiIntent.INBOUND_RECEIPT_DETAIL);
        assertThat(receipt.safeParameters()).containsEntry("receiptCode", "GR-2026-015");
        assertThat(picking.getIntent()).isEqualTo(AiIntent.PICKING_STATUS);
        assertThat(picking.safeParameters()).containsEntry("pickingTaskCode", "PK-2026-009");
    }

    @Test
    void routesNewAiCoverageIntents() {
        assertThat(router.route("Tôi có thông báo mới nào không?", List.of()).getIntent())
                .isEqualTo(AiIntent.NOTIFICATION_LIST);
        assertThat(router.route("Hôm nay tôi được giao việc gì?", List.of()).getIntent())
                .isEqualTo(AiIntent.MY_TASKS);
        assertThat(router.route("Hệ thống có những vai trò nào?", List.of()).getIntent())
                .isEqualTo(AiIntent.ROLE_LIST);
        assertThat(router.route("User an.nguyen có vai trò gì?", List.of()).getIntent())
                .isEqualTo(AiIntent.USER_LOOKUP);
        assertThat(router.route("Có những danh mục sản phẩm nào?", List.of()).getIntent())
                .isEqualTo(AiIntent.CATEGORY_LIST);
        assertThat(router.route("Sản phẩm nào thuộc danh mục Điện thoại?", List.of()).getIntent())
                .isEqualTo(AiIntent.PRODUCT_BY_CATEGORY);
        assertThat(router.route("Vị trí A-01-02 đang chứa hàng gì?", List.of()).getIntent())
                .isEqualTo(AiIntent.STOCK_BY_LOCATION);
        assertThat(router.route("Lô L20260315 còn bao nhiêu, ở đâu?", List.of()).getIntent())
                .isEqualTo(AiIntent.STOCK_BY_LOT);
        assertThat(router.route("Hàng chết trên 90 ngày gồm SKU nào?", List.of()).getIntent())
                .isEqualTo(AiIntent.DEAD_STOCK);
        assertThat(router.route("Lô gần hết hạn còn tồn lớn có rủi ro không?", List.of()).getIntent())
                .isEqualTo(AiIntent.STOCK_AT_RISK);
        assertThat(router.route("Gợi ý SKU cần đặt hàng bổ sung", List.of()).getIntent())
                .isEqualTo(AiIntent.REORDER_SUGGESTION);
        assertThat(router.route("Top khách hàng theo doanh số tháng này?", List.of()).getIntent())
                .isEqualTo(AiIntent.TOP_CUSTOMERS);
        assertThat(router.route("Tỷ lệ fulfillment đơn xuất tháng này là bao nhiêu?", List.of()).getIntent())
                .isEqualTo(AiIntent.FULFILLMENT_RATE);
        assertThat(router.route("Tỷ lệ lấp đầy kho WH-002 là bao nhiêu?", List.of()).getIntent())
                .isEqualTo(AiIntent.WAREHOUSE_CAPACITY);
        assertThat(router.route("Năng suất picking theo người hôm nay?", List.of()).getIntent())
                .isEqualTo(AiIntent.PICKING_PRODUCTIVITY);
        assertThat(router.route("Hiệu suất nhà cung cấp giao đúng hạn tháng này?", List.of()).getIntent())
                .isEqualTo(AiIntent.SUPPLIER_PERFORMANCE);
    }

    @Test
    void routesExpandedOperationalAnalyticsIntents() {
        assertThat(router.route("Giá trị tồn kho của kho WH-003 so với WH-001 như thế nào?", List.of()).getIntent())
                .isEqualTo(AiIntent.INVENTORY_VALUE_BY_WAREHOUSE);
        assertThat(router.route("Lô hàng nào có hạn sử dụng dài nhất còn lại?", List.of()).getIntent())
                .isEqualTo(AiIntent.LONGEST_EXPIRY_STOCK);
        assertThat(router.route("Có bao nhiêu sản phẩm đang ở trạng thái ngừng kinh doanh nhưng vẫn còn tồn?", List.of()).getIntent())
                .isEqualTo(AiIntent.INACTIVE_PRODUCT_WITH_STOCK);
        assertThat(router.route("Số lượng nhập kho của sản phẩm \"Quả Lê Cao Cấp\" trong tuần này?", List.of()).getIntent())
                .isEqualTo(AiIntent.INBOUND_PRODUCT_QTY);
        assertThat(router.route("Phiếu nhập kho nào đã được duyệt nhưng chưa putaway?", List.of()).getIntent())
                .isEqualTo(AiIntent.INBOUND_PENDING_PUTAWAY);
        assertThat(router.route("Số lượng lô hàng nhập kho trung bình mỗi ngày trong tuần qua?", List.of()).getIntent())
                .isEqualTo(AiIntent.INBOUND_AVG_DAILY);
        assertThat(router.route("Tổng số lượng xuất kho trong ngày hôm nay là bao nhiêu?", List.of()).getIntent())
                .isEqualTo(AiIntent.OUTBOUND_TOTAL_QTY);
        assertThat(router.route("Những đơn xuất kho nào có nguy cơ trễ hạn giao hàng?", List.of()).getIntent())
                .isEqualTo(AiIntent.OUTBOUND_DELAYED);
        assertThat(router.route("Tổng số đơn xuất kho đã hoàn thành picking trong tuần qua?", List.of()).getIntent())
                .isEqualTo(AiIntent.PICKING_COMPLETED_COUNT);
        assertThat(router.route("Sản phẩm Rovi Bút bi xanh loại cao cấp có đủ hàng để picking 30 cái không?", List.of()).getIntent())
                .isEqualTo(AiIntent.PICKING_STOCK_CHECK);
        assertThat(router.route("Tỷ lệ hoàn thành kiểm kê theo kế hoạch hiện là bao nhiêu?", List.of()).getIntent())
                .isEqualTo(AiIntent.CYCLE_COUNT_COMPLETION_RATE);
        assertThat(router.route("Nguyên nhân trả hàng được ghi trong RMA mới nhất là gì?", List.of()).getIntent())
                .isEqualTo(AiIntent.RMA_LATEST_REASON);
        assertThat(router.route("Tỷ lệ RMA được chấp nhận so với tổng RMA là bao nhiêu?", List.of()).getIntent())
                .isEqualTo(AiIntent.RMA_RATE);
        assertThat(router.route("Tỷ lệ hoàn thành picking và putaway trong tuần qua?", List.of()).getIntent())
                .isEqualTo(AiIntent.TASK_COMPLETION_RATE);
    }

    @Test
    void routesNextOperationalGapIntents() {
        assertThat(router.route("Sản phẩm nào giảm tồn kho nhanh nhất trong kho WH-001 trong 7 ngày qua?", List.of()).getIntent())
                .isEqualTo(AiIntent.STOCK_FASTEST_DECREASE);
        assertThat(router.route("Hiệu suất pick/putaway/packing của nhân viên cụ thể là thế nào?", List.of()).getIntent())
                .isEqualTo(AiIntent.EMPLOYEE_OPERATION_PRODUCTIVITY);
        assertThat(router.route("Có bao nhiêu task đang quá hạn xử lý?", List.of()).getIntent())
                .isEqualTo(AiIntent.OVERDUE_TASKS);
        assertThat(router.route("Vị trí lấy hàng nào đang được dùng nhiều nhất cho xuất kho?", List.of()).getIntent())
                .isEqualTo(AiIntent.PICK_LOCATION_USAGE);
        assertThat(router.route("Có bao nhiêu đơn xuất kho bị dừng do thiếu hàng?", List.of()).getIntent())
                .isEqualTo(AiIntent.OUTBOUND_SHORTAGE);
        assertThat(router.route("Lý do xuất chậm nhiều nhất thường là gì?", List.of()).getIntent())
                .isEqualTo(AiIntent.OUTBOUND_DELAY_REASON);
        assertThat(router.route("Danh sách SKU cần kiểm kê lại sau khi phát hiện sai số.", List.of()).getIntent())
                .isEqualTo(AiIntent.CYCLE_COUNT_RECOUNT_SKUS);
        assertThat(router.route("RMA nào cần kiểm tra chất lượng hàng trước khi nhập lại?", List.of()).getIntent())
                .isEqualTo(AiIntent.RMA_QC_REQUIRED);
        assertThat(router.route("Có bao nhiêu lỗi ghi nhận được báo cáo hôm nay?", List.of()).getIntent())
                .isEqualTo(AiIntent.OPERATION_ISSUE_REPORT);
    }

    @Test
    void routesCurrentUserTaskPhrasesToMyTasks() {
        assertThat(router.route("Đơn nào tôi đang pick dở?", List.of()).getIntent())
                .isEqualTo(AiIntent.MY_TASKS);
        assertThat(router.route("Task putaway nào đang chờ tôi xử lý?", List.of()).getIntent())
                .isEqualTo(AiIntent.MY_TASKS);
        assertThat(router.route("Hôm nay tôi còn việc gì chưa xong?", List.of()).getIntent())
                .isEqualTo(AiIntent.MY_TASKS);
    }

    @Test
    void routesRepresentativeBusinessQuestionGroups() {
        assertThat(router.route("Sản phẩm nào sắp hết hàng?", List.of()).getIntent())
                .isEqualTo(AiIntent.LOW_STOCK);
        assertThat(router.route("Kho nào còn nhiều hàng nhất?", List.of()).getIntent())
                .isEqualTo(AiIntent.WAREHOUSE_STOCK_SUMMARY);
        assertThat(router.route("Hôm nay có bao nhiêu đơn nhập?", List.of()).getIntent())
                .isEqualTo(AiIntent.INBOUND_TODAY);
        assertThat(router.route("Tồn kho hiện tại của SKU 00018 là bao nhiêu?", List.of()).getIntent())
                .isEqualTo(AiIntent.STOCK_BY_PRODUCT);
        assertThat(router.route("Đơn xuất nào đang chờ xử lý?", List.of()).getIntent())
                .isEqualTo(AiIntent.OUTBOUND_PRIORITY);
        assertThat(router.route("Nhân viên nào xử lý đơn nhập gần nhất?", List.of()).getIntent())
                .isEqualTo(AiIntent.LATEST_INBOUND);
        assertThat(router.route("Có sản phẩm nào chưa được gán vị trí không?", List.of()).getIntent())
                .isEqualTo(AiIntent.PRODUCT_WITHOUT_LOCATION);
        assertThat(router.route("Thống kê nhập xuất theo tháng.", List.of()).getIntent())
                .isEqualTo(AiIntent.MONTHLY_REPORT);
        assertThat(router.route("Gợi ý nhập thêm hàng dựa trên tồn kho thấp.", List.of()).getIntent())
                .isEqualTo(AiIntent.REORDER_SUGGESTION);
    }
}
