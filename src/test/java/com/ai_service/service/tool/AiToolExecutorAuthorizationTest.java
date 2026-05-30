package com.ai_service.service.tool;

import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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

    private static void authenticateAs(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "n/a",
                        List.of(new SimpleGrantedAuthority(authority))));
    }
}
