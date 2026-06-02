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
        assertThat(reply).contains("**Tổng số lượng còn phải nhận:** **2.350** đơn vị");
        assertThat(reply).contains("- **PO-2026-000180**");
        assertThat(reply).contains("\n  - Trạng thái:");
        assertThat(reply).contains("**Khuyến nghị:** ưu tiên xác nhận lịch giao");
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
        assertThat(reply).contains("**Khuyến nghị:** kiểm tra tồn khả dụng");
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
        assertThat(reply).contains("**Tổng số lượng cần xếp kệ:** **15** đơn vị");
        assertThat(reply).contains("- Bút Bi TL-027");
        assertThat(reply).contains("\n  - Vị trí gợi ý:");
        assertThat(reply).contains("**Khuyến nghị:** xử lý trước các task");
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
        assertThat(reply).contains("**Nhận định:** một phần tồn đang được giữ chỗ");
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

    @Test
    void answersGenericProductAvailabilityAcrossAllWarehouses() {
        String reply = composer.compose(
                "Banh quy bo có trong kho không",
                AiIntentResult.of(AiIntent.STOCK_BY_PRODUCT, Map.of("query", "Banh quy bo có trong kho không"), 0.9, "test"),
                AiToolResult.data("StockByProductTool", List.of(
                        Map.of(
                                "sku", "SP-BANH-QUY-BO",
                                "product_name", "Bánh quy bơ",
                                "warehouse_code", "WH-HN",
                                "qty_on_hand", 4,
                                "qty_reserved", 0,
                                "qty_available", 4),
                        Map.of(
                                "sku", "SP-BANH-QUY-BO",
                                "product_name", "Bánh quy bơ",
                                "warehouse_code", "WH-HCM",
                                "qty_on_hand", 6,
                                "qty_reserved", 1,
                                "qty_available", 5)
                )),
                List.of());

        assertThat(reply).startsWith("Có,");
        assertThat(reply).contains("**10** đơn vị");
        assertThat(reply).contains("**WH-HN**");
        assertThat(reply).contains("**WH-HCM**");
        assertThat(reply).contains("**Nhận định:** một phần tồn đang được giữ chỗ");
    }

    @Test
    void answersSpecificWarehouseMissWithOtherWarehouseAvailability() {
        String reply = composer.compose(
                "Banh quy bo có trong kho Hà Nội không",
                AiIntentResult.of(AiIntent.STOCK_BY_PRODUCT, Map.of(
                        "query", "Banh quy bo có trong kho Hà Nội không",
                        "warehouseCode", "WH-HN"), 0.9, "test"),
                AiToolResult.data("StockByProductTool", List.of(
                        Map.of(
                                "sku", "SP-BANH-QUY-BO",
                                "product_name", "Bánh quy bơ",
                                "warehouse_code", "WH-HN",
                                "requested_warehouse_code", "WH-HN",
                                "qty_on_hand", 0,
                                "qty_reserved", 0,
                                "qty_available", 0),
                        Map.of(
                                "sku", "SP-BANH-QUY-BO",
                                "product_name", "Bánh quy bơ",
                                "warehouse_code", "WH-HCM",
                                "requested_warehouse_code", "WH-HN",
                                "qty_on_hand", 6,
                                "qty_reserved", 0,
                                "qty_available", 6)
                )),
                List.of());

        assertThat(reply).startsWith("Không,");
        assertThat(reply).contains("kho **WH-HN**");
        assertThat(reply).contains("nhưng còn ở kho khác");
        assertThat(reply).contains("**WH-HCM** (6 đơn vị)");
    }

    @Test
    void answersZeroQuantityWithoutEmptyWarehouseSection() {
        String reply = composer.compose(
                "Bàn phím cơ còn bao nhiêu cái?",
                AiIntentResult.of(AiIntent.STOCK_BY_PRODUCT, Map.of("query", "Bàn phím cơ còn bao nhiêu cái?"), 0.9, "test"),
                AiToolResult.data("StockByProductTool", List.of(
                        Map.of(
                                "sku", "SP-BAN-PHIM",
                                "product_name", "Bàn Phím Cơ",
                                "warehouse_code", "N/A",
                                "qty_on_hand", 0,
                                "qty_reserved", 0,
                                "qty_available", 0)
                )),
                List.of());

        assertThat(reply).contains("Chưa có tồn ở bất kỳ kho nào");
        assertThat(reply).doesNotContain("Chi tiết theo từng kho");
    }

    @Test
    void asksForSpecificMissingParameterWithExample() {
        String reply = composer.compose(
                "Còn bao nhiêu hàng?",
                AiIntentResult.of(AiIntent.STOCK_BY_PRODUCT, Map.of("query", "Còn bao nhiêu hàng?"), 0.88, "test"),
                AiToolResult.message("StockTool.getStockByProduct", "Cần tên sản phẩm hoặc SKU.")
                        .withMetadata(List.of("products", "stock_levels"), List.of("sku|product")),
                List.of());

        assertThat(reply).contains("**Mình chưa đủ thông tin để kiểm tra tồn kho sản phẩm.**");
        assertThat(reply).contains("SKU hoặc tên sản phẩm");
        assertThat(reply).contains("SKU 00018 còn bao nhiêu trong kho WH-001?");
    }

    @Test
    void clarifiesAmbiguousQuestionWithConcreteExamples() {
        String reply = composer.compose(
                "Kiểm tra giúp tôi",
                AiIntentResult.of(AiIntent.AMBIGUOUS, Map.of("query", "Kiểm tra giúp tôi"), 0.5, "test"),
                AiToolResult.message("Clarification", "Bạn vui lòng nói rõ thêm mã kho, SKU, đơn hàng hoặc khoảng thời gian cần kiểm tra."),
                List.of());

        assertThat(reply).contains("**Mình cần bạn làm rõ thêm câu hỏi.**");
        assertThat(reply).contains("SKU 00018 còn bao nhiêu?");
        assertThat(reply).contains("Đơn xuất nào đang thiếu hàng?");
    }

    @Test
    void explainsWhenWarehouseRowsAreMissingForWhichWarehouseQuestion() {
        String reply = composer.compose(
                "Sản phẩm iPhone còn ở kho nào?",
                AiIntentResult.of(AiIntent.STOCK_BY_PRODUCT, Map.of("query", "Sản phẩm iPhone còn ở kho nào?"), 0.9, "test"),
                AiToolResult.data("StockByProductTool", List.of(
                        Map.of(
                                "sku", "SP-IPHONE",
                                "product_name", "iPhone 15 Pro Max",
                                "warehouse_code", "N/A",
                                "qty_on_hand", 20,
                                "qty_reserved", 0,
                                "qty_available", 20)
                )),
                List.of());

        assertThat(reply).contains("có tồn tổng **20** đơn vị");
        assertThat(reply).contains("chưa gắn được kho cụ thể");
        assertThat(reply).doesNotContain("chưa xác định kho");
    }

    @Test
    void hidesOperationalTechnicalIdsInUserFacingLists() {
        String uuid = "00000000-0000-0000-0000-000000000001";
        String pickingReply = composer.compose(
                "Ai đang có nhiều việc picking nhất?",
                AiIntentResult.of(AiIntent.PICKING_PRODUCTIVITY, Map.of("query", "Ai đang có nhiều việc picking nhất?"), 0.9, "test"),
                AiToolResult.data("PickingProductivityTool", List.of(
                        Map.of("assignee", "Unassigned", "qty_picked", 0, "qty_to_pick", 10, "completed_lines", 0),
                        Map.of("assignee", uuid, "qty_picked", 1, "qty_to_pick", 5, "completed_lines", 1)
                )),
                List.of());
        String putawayReply = composer.compose(
                "Có task putaway nào đang chờ lâu không?",
                AiIntentResult.of(AiIntent.PENDING_PUTAWAY, Map.of("query", "Có task putaway nào đang chờ lâu không?"), 0.9, "test"),
                AiToolResult.data("PendingPutawayTool", List.of(
                        Map.of(
                                "sku", "SP-UNKNOWN",
                                "product_name", "Sản phẩm 019e213e-d3f8-7e34-8a1c-d5c0819afe10",
                                "qty_to_putaway", 20,
                                "status", "PENDING",
                                "suggested_location", "HCM-TT-A01")
                )),
                List.of());

        assertThat(pickingReply).contains("Chưa gán nhân viên");
        assertThat(pickingReply).doesNotContain("Unassigned");
        assertThat(pickingReply).doesNotContain(uuid);
        assertThat(putawayReply).contains("SKU `SP-UNKNOWN`");
        assertThat(putawayReply).doesNotContain("019e213e-d3f8-7e34-8a1c-d5c0819afe10");
    }
}
