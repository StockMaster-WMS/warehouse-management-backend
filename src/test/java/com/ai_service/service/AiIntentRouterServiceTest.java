package com.ai_service.service;

import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
