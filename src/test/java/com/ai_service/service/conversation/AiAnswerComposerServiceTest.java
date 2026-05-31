package com.ai_service.service.conversation;

import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiAnswerComposerServiceTest {

    private final AiAnswerComposerService composer = new AiAnswerComposerService(null, new ObjectMapper());

    @Test
    void formatsPendingPurchaseOrdersAsReadableList() {
        String reply = composer.compose(
                "Có phiếu nhập nào đang chờ nhận hàng không?",
                AiIntentResult.of(AiIntent.PENDING_PO_RECEIPT, Map.of(), 0.9, "test"),
                AiToolResult.data("PendingPoReceiptTool", List.of(
                        Map.of(
                                "po_number", "PO-2026-000180",
                                "supplier_name", "Công ty Sao Mai",
                                "status", "DRAFT",
                                "remaining_qty", 940),
                        Map.of(
                                "po_number", "PO-2026-000360",
                                "supplier_name", "Công ty Khải Hoàn",
                                "status", "APPROVED",
                                "remaining_qty", 1410)
                )),
                List.of());

        assertThat(reply).contains("**Có 2 PO đang chờ nhận/chưa hoàn tất:**");
        assertThat(reply).contains("- **PO-2026-000180**");
        assertThat(reply).contains("\n  - Trạng thái:");
        assertThat(reply).doesNotContain("PO-2026-000180 trạng thái");
    }

    @Test
    void formatsOutboundShortageAsSummaryAndBullets() {
        String reply = composer.compose(
                "Đơn nào còn thiếu hàng để giao?",
                AiIntentResult.of(AiIntent.OUTBOUND_SHORTAGE, Map.of(), 0.9, "test"),
                AiToolResult.data("OutboundShortageTool", Map.of(
                        "summary", Map.of("orders", 768, "shortage_qty", 147303),
                        "items", List.of(
                                Map.of("so_number", "SO-2026-000198", "sku", "BEV-00491", "shortage_qty", 77),
                                Map.of("so_number", "SO-2026-000396", "sku", "FMCG-00987", "shortage_qty", 55)
                        )
                )),
                List.of());

        assertThat(reply).contains("**Có 768 đơn xuất thiếu hàng để giao.**");
        assertThat(reply).contains("**Một số dòng cần xử lý:**");
        assertThat(reply).contains("- **SO-2026-000198**");
    }

    @Test
    void formatsPutawayTasksAsBullets() {
        String reply = composer.compose(
                "Có task putaway nào đang chờ lâu không?",
                AiIntentResult.of(AiIntent.PENDING_PUTAWAY, Map.of(), 0.9, "test"),
                AiToolResult.data("PendingPutawayTool", List.of(
                        Map.of(
                                "product_name", "Bút Bi TL-027",
                                "qty_to_putaway", 15,
                                "status", "PENDING",
                                "suggested_location", "HCM-TT-HEAVY-A01-R09-L02-B01")
                )),
                List.of());

        assertThat(reply).contains("**Có 1 putaway task đang chờ/xử lý:**");
        assertThat(reply).contains("- Bút Bi TL-027");
        assertThat(reply).contains("\n  - Vị trí gợi ý:");
    }

    @Test
    void answersProductExistenceQuestionWithExplicitYes() {
        String reply = composer.compose(
                "Xinh Ủng bảo hộ cao su đóng gói thương mại có trong kho không",
                AiIntentResult.of(AiIntent.STOCK_BY_PRODUCT, Map.of(
                        "query", "Xinh Ủng bảo hộ cao su đóng gói thương mại có trong kho không"), 0.9, "test"),
                AiToolResult.data("StockByProductTool", List.of(
                        Map.of(
                                "sku", "FROZ-00634",
                                "product_name", "Xinh Ủng bảo hộ cao su đóng gói thương mại",
                                "warehouse_code", "HN-TT",
                                "qty_on_hand", 12,
                                "qty_reserved", 2,
                                "qty_available", 10)
                )),
                List.of());

        assertThat(reply).startsWith("Có,");
        assertThat(reply).contains("hiện có trong kho");
        assertThat(reply).contains("**HN-TT**");
    }

    @Test
    void answersMissingProductExistenceQuestionWithExplicitNo() {
        String reply = composer.compose(
                "Banh quy bo có trong kho không",
                AiIntentResult.of(AiIntent.STOCK_BY_PRODUCT, Map.of("query", "Banh quy bo có trong kho không"), 0.9, "test"),
                AiToolResult.data("StockByProductTool", List.of()),
                List.of());

        assertThat(reply).startsWith("Không,");
    }
}
