package com.ai_service.service.conversation;

import com.ai_service.context.AiQueryContext;
import com.ai_service.dto.AiAskResponse.AiResponseMetadata;
import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiResponseEnrichmentServiceTest {

    private final AiResponseEnrichmentService service = new AiResponseEnrichmentService();

    @Test
    void includesOperationalMetadataAndSuggestedQuestions() {
        AiIntentResult route = AiIntentResult.of(
                AiIntent.STOCK_BY_PRODUCT,
                Map.of("sku", "AIRCON-DAIKIN"),
                0.94,
                "test");
        AiToolResult toolResult = AiToolResult
                .data("StockTool.getStockByProduct", List.of(Map.of("sku", "AIRCON-DAIKIN")))
                .withMetadata(List.of("products", "stock_levels", "warehouses"), List.of());
        AiQueryContext context = AiQueryContext.from("AIRCON còn bao nhiêu?", route, toolResult, 1);

        AiResponseMetadata metadata = service.build(route, toolResult, context);

        assertThat(metadata.intent()).isEqualTo("STOCK_BY_PRODUCT");
        assertThat(metadata.confidence()).isEqualTo(0.94);
        assertThat(metadata.domain()).isEqualTo("inventory");
        assertThat(metadata.toolName()).isEqualTo("StockTool.getStockByProduct");
        assertThat(metadata.dataSources()).containsExactly("products", "stock_levels", "warehouses");
        assertThat(metadata.rowsReturned()).isEqualTo(1);
        assertThat(metadata.parameters()).containsEntry("sku", "AIRCON-DAIKIN");
        assertThat(metadata.suggestedQuestions()).contains("Lịch sử biến động 7 ngày qua?");
        assertThat(metadata.intentQuality()).isEqualTo("HIGH");
        assertThat(metadata.needsClarification()).isFalse();
        assertThat(metadata.qualitySignals()).contains("confidence:high", "dataBacked:true", "rowsReturned:1");
        assertThat(metadata.display()).containsEntry("type", "table");
        assertThat(metadata.display()).containsEntry("title", "Tồn kho sản phẩm");
        assertThat(metadata.resultRows()).hasSize(1);
        assertThat(metadata.resultRows().get(0)).containsEntry("sku", "AIRCON-DAIKIN");
    }

    @Test
    void includesCandidateSuggestionsFromToolMetadata() {
        AiIntentResult route = AiIntentResult.of(
                AiIntent.STOCK_BY_PRODUCT,
                Map.of("query", "ban quy bo con hang khong"),
                0.9,
                "test");
        AiToolResult toolResult = AiToolResult
                .message("StockTool.getStockByProduct", "Có phải bạn muốn hỏi Bánh quy bơ?")
                .withUiMetadata(Map.of(
                        "display", Map.of("type", "candidate_list", "title", "Có phải bạn muốn hỏi?"),
                        "candidateSuggestions", List.of(Map.of(
                                "sku", "SP-BANH-QUY-BO",
                                "product_name", "Bánh quy bơ",
                                "query", "SKU SP-BANH-QUY-BO còn hàng không?"
                        ))
                ));
        AiQueryContext context = AiQueryContext.from("ban quy bo con hang khong", route, toolResult, 0);

        AiResponseMetadata metadata = service.build(route, toolResult, context);

        assertThat(metadata.display()).containsEntry("type", "candidate_list");
        assertThat(metadata.candidateSuggestions()).hasSize(1);
        assertThat(metadata.candidateSuggestions().get(0))
                .containsEntry("sku", "SP-BANH-QUY-BO")
                .containsEntry("query", "SKU SP-BANH-QUY-BO còn hàng không?");
    }

    @Test
    void addsSafeDraftActionForStockTransferRequests() {
        AiIntentResult route = AiIntentResult.of(
                AiIntent.STOCK_TRANSFER,
                Map.of("sku", "AIRCON-DAIKIN", "quantity", 20),
                0.9,
                "test");
        AiToolResult toolResult = AiToolResult.message("StockTool.transferGuide", "guide");
        AiQueryContext context = AiQueryContext.from("Chuyển 20 cái AIRCON", route, toolResult, 0);

        AiResponseMetadata metadata = service.build(route, toolResult, context);

        assertThat(metadata.actions()).hasSize(1);
        assertThat(metadata.actions().get(0).type()).isEqualTo("CREATE_STOCK_TRANSFER_DRAFT");
        assertThat(metadata.actions().get(0).requiresConfirmation()).isTrue();
        assertThat(metadata.actions().get(0).requiresAuthority()).isEqualTo("WAREHOUSE_MANAGER");
    }

    @Test
    void removesSensitiveParametersFromMetadata() {
        AiIntentResult route = AiIntentResult.of(
                AiIntent.GENERAL_GUIDE,
                Map.of("query", "hello", "password", "secret-value", "apiKey", "key"),
                0.8,
                "test");
        AiToolResult toolResult = AiToolResult.message("GeneralGuide", "hello");
        AiQueryContext context = AiQueryContext.from("hello", route, toolResult, 0);

        AiResponseMetadata metadata = service.build(route, toolResult, context);

        assertThat(metadata.parameters()).containsEntry("query", "hello");
        assertThat(metadata.parameters()).doesNotContainKeys("password", "apiKey");
    }

    @Test
    void marksMissingRequiredParametersAsClarificationNeeded() {
        AiIntentResult route = AiIntentResult.of(
                AiIntent.STOCK_BY_PRODUCT,
                Map.of("query", "còn bao nhiêu hàng?"),
                0.88,
                "test");
        AiToolResult toolResult = AiToolResult
                .message("StockTool.getStockByProduct", "Cần tên sản phẩm hoặc SKU.")
                .withMetadata(List.of("products", "stock_levels"), List.of("sku|product"));
        AiQueryContext context = AiQueryContext.from("còn bao nhiêu hàng?", route, toolResult, 0);

        AiResponseMetadata metadata = service.build(route, toolResult, context);

        assertThat(metadata.intentQuality()).isEqualTo("HIGH");
        assertThat(metadata.needsClarification()).isTrue();
        assertThat(metadata.clarificationReason()).isEqualTo("missing_parameters");
        assertThat(metadata.qualitySignals()).contains("missingParams:1", "dataBacked:false");
        assertThat(metadata.suggestedQuestions()).contains("Bạn muốn kiểm tra mã kho, SKU hay mã đơn nào?");
    }

    @Test
    void marksAmbiguousIntentAsLowQualityClarification() {
        AiIntentResult route = AiIntentResult.of(
                AiIntent.AMBIGUOUS,
                Map.of("query", "kiểm tra giúp tôi"),
                0.5,
                "test");
        AiToolResult toolResult = AiToolResult.message("Clarification", "Bạn vui lòng nói rõ thêm.");
        AiQueryContext context = AiQueryContext.from("kiểm tra giúp tôi", route, toolResult, 0);

        AiResponseMetadata metadata = service.build(route, toolResult, context);

        assertThat(metadata.intentQuality()).isEqualTo("LOW");
        assertThat(metadata.needsClarification()).isTrue();
        assertThat(metadata.clarificationReason()).isEqualTo("ambiguous_intent");
        assertThat(metadata.qualitySignals()).contains("confidence:low", "intent:ambiguous");
    }
}
