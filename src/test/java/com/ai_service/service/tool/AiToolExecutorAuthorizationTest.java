package com.ai_service.service.tool;

import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiToolExecutorAuthorizationTest {

    private final AiToolExecutorService service = new AiToolExecutorService(mock(JdbcTemplate.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportViewerCannotUseCustomerDetailTool() {
        authenticateAs("REPORT_VIEWER");

        AiToolResult result = service.execute(AiIntentResult.of(
                AiIntent.CUSTOMER_DETAIL,
                Map.of("query", "customer"),
                0.9,
                "test"));

        assertThat(result.dataBacked()).isFalse();
        assertThat(result.toolName()).isEqualTo("Authorization.forbidden");
    }

    @Test
    void reportViewerCanUseGeneralGuide() {
        authenticateAs("REPORT_VIEWER");

        AiToolResult result = service.execute(AiIntentResult.of(
                AiIntent.GENERAL_GUIDE,
                Map.of("query", "huong dan tao don nhap"),
                0.9,
                "test"));

        assertThat(result.dataBacked()).isFalse();
        assertThat(result.toolName()).isEqualTo("GeneralGuide");
        assertThat(result.message()).contains("Để tạo phiếu nhập");
    }

    @ParameterizedTest
    @CsvSource({
            "'có sản phẩm Xoài cát trong kho không','Xoài cát'",
            "'có sản phẩm Bàn phím cơ trong kho không','Bàn phím cơ'",
            "'có mặt hàng Sữa tươi ở kho không','Sữa tươi'",
            "'Xinh Ủng bảo hộ cao su đóng gói thương mại có trong kho không','Xinh Ủng bảo hộ cao su đóng gói thương mại'",
            "'Banh quy bo có trong kho không?','Banh quy bo'"
    })
    void cleansNaturalProductAvailabilityQuestionToProductKeyword(String question, String expectedKeyword) throws Exception {
        Method method = AiToolExecutorService.class.getDeclaredMethod("cleanProductKeyword", String.class);
        method.setAccessible(true);

        String keyword = (String) method.invoke(service, question);

        assertThat(keyword).isEqualTo(expectedKeyword);
    }

    @Test
    void resolvesProductNameAccentInsensitivelyWithoutWeakSubstringMatch() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiToolExecutorService service = new AiToolExecutorService(jdbcTemplate);
        when(jdbcTemplate.queryForList(contains("FROM products")))
                .thenReturn(List.of(
                        Map.of("sku", "SP-260416011544171-5C6C", "name", "Cá Thu Nhật"),
                        Map.of("sku", "SP-BANH-QUY-BO", "name", "Bánh quy bơ")
                ));
        Method method = AiToolExecutorService.class.getDeclaredMethod("resolveProductSku", String.class);
        method.setAccessible(true);

        String cookieSku = (String) method.invoke(service, "Banh quy bo có trong kho không?");
        String wrongSku = (String) method.invoke(service,
                "Xinh Ủng bảo hộ cao su đóng gói thương mại có trong kho không");

        assertThat(cookieSku).isEqualTo("SP-BANH-QUY-BO");
        assertThat(wrongSku).isNull();
    }

    @Test
    void treatsGenericAvailabilityQuestionAsAllWarehouses() throws Exception {
        Method method = AiToolExecutorService.class.getDeclaredMethod("hasWarehouseHint", Map.class);
        method.setAccessible(true);

        boolean generic = (boolean) method.invoke(service,
                Map.of("query", "Banh quy bo có trong kho không"));
        boolean hcm = (boolean) method.invoke(service,
                Map.of("query", "Banh quy bo có trong kho Hồ Chí Minh không"));

        assertThat(generic).isFalse();
        assertThat(hcm).isTrue();
    }

    @Test
    void pendingPoReceiptExcludesDraftCancelledAndCompletedOrders() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());
        AiToolExecutorService service = new AiToolExecutorService(jdbcTemplate);
        Method method = AiToolExecutorService.class.getDeclaredMethod("getPendingPoReceipt");
        method.setAccessible(true);

        method.invoke(service);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture());
        assertThat(sql.getValue()).contains("po.status IN ('APPROVED', 'PARTIAL')");
        assertThat(sql.getValue()).contains("HAVING COALESCE(SUM(pi.ordered_qty - pi.received_qty), 0) > 0");
        assertThat(sql.getValue()).doesNotContain("po.status <> 'COMPLETED'");
    }

    private static void authenticateAs(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "n/a",
                        List.of(new SimpleGrantedAuthority(authority))));
    }
}
