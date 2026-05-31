package com.ai_service.service;

import com.ai_service.context.AiQueryContext;
import com.ai_service.dto.AiAskRequest;
import com.ai_service.dto.AiAskResponse;
import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.service.conversation.AiAnswerComposerService;
import com.ai_service.service.conversation.AiIntentRouterService;
import com.ai_service.service.conversation.AiResponseEnrichmentService;
import com.ai_service.service.session.AiAuditService;
import com.ai_service.service.session.AiCancelService;
import com.ai_service.service.session.AiHistoryService;
import com.ai_service.service.tool.AiToolExecutorService;
import com.ai_service.tool.AiToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiServiceBatchQuestionTest {

    @Test
    void answersEachLineAsSeparateQuestion() {
        AiIntentRouterService router = mock(AiIntentRouterService.class);
        AiToolExecutorService toolExecutor = mock(AiToolExecutorService.class);
        AiAnswerComposerService answerComposer = mock(AiAnswerComposerService.class);
        AiHistoryService historyService = mock(AiHistoryService.class);
        AiAuditService auditService = mock(AiAuditService.class);
        AiCancelService cancelService = mock(AiCancelService.class);
        AiService service = new AiService(
                router,
                toolExecutor,
                answerComposer,
                new AiResponseEnrichmentService(),
                historyService,
                auditService,
                cancelService,
                new ObjectMapper());

        when(historyService.getMessages("s1")).thenReturn(List.of());
        when(router.route(anyString(), anyList())).thenReturn(AiIntentResult.of(
                AiIntent.STOCK_BY_PRODUCT,
                Map.of("query", "test"),
                0.9,
                "test"));
        when(toolExecutor.execute(any())).thenReturn(AiToolResult
                .data("StockTool.getStockByProduct", List.of(Map.of("sku", "SKU-1")))
                .withMetadata(List.of("products", "stock_levels"), List.of()));
        when(toolExecutor.estimateRows(any())).thenReturn(1);
        when(answerComposer.compose(anyString(), any(), any(), anyList(), any(AiQueryContext.class)))
                .thenAnswer(invocation -> "Trả lời cho: " + invocation.getArgument(0, String.class));

        AiAskRequest request = new AiAskRequest();
        request.setSessionId("s1");
        request.setQuestion("""
                Có Xoài cát trong kho không?
                Bàn phím cơ còn bao nhiêu cái?
                Hôm nay có hàng nào mới nhập không?
                """);

        AiAskResponse response = service.ask(request);

        assertThat(response.getIntent()).isEqualTo("MULTI_QUESTION");
        assertThat(response.getRowsReturned()).isEqualTo(3);
        assertThat(response.getReply()).contains("1. Có Xoài cát trong kho không?");
        assertThat(response.getReply()).contains("2. Bàn phím cơ còn bao nhiêu cái?");
        assertThat(response.getReply()).contains("3. Hôm nay có hàng nào mới nhập không?");
        verify(router, times(3)).route(anyString(), anyList());
        verify(toolExecutor, times(3)).execute(any());
        verify(auditService).log(anyString(), anyString(), anyString(), anyInt(), any(), anyLong());
    }
}
