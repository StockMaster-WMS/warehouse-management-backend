package com.ai_putway.controller;

import com.ai_putway.dto.ChatRequest;
import com.ai_putway.dto.ChatResponse;
import com.ai_putway.dto.LocationSuggestionRequest;
import com.ai_putway.dto.LocationSuggestionResponse;
import com.ai_putway.service.AIBridgeService;
import com.common.api.ApiResponse;
import com.warehouse_service.service.WarehouseAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Putaway", description = "API kết nối AI Engine cho gợi ý vị trí xếp hàng")
public class AIController {

    private final AIBridgeService aiBridgeService;
    private final WarehouseAccessService warehouseAccessService;

    @PostMapping("/suggest-locations")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Gợi ý vị trí xếp hàng bằng AI")
    public ApiResponse<LocationSuggestionResponse> suggestLocations(
            @Valid @RequestBody LocationSuggestionRequest request,
            Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.getWarehouseId());
        LocationSuggestionResponse result = aiBridgeService.suggestLocations(
                request, currentUserId(authentication)
        );
        return ApiResponse.success("Gợi ý vị trí xếp hàng thành công", result);
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'REPORT_VIEWER')")
    @Operation(summary = "Chat với AI Engine putaway")
    public ApiResponse<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        ChatResponse result = aiBridgeService.chat(
                request.getQuestion(),
                currentUserId(authentication),
                warehouseAccessService.visibleWarehouseIds(authentication)
        );
        return ApiResponse.success("Chat AI thành công", result);
    }

    @GetMapping("/engine/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Kiểm tra trạng thái AI Engine putaway")
    public ApiResponse<Boolean> status() {
        return ApiResponse.success("Lấy trạng thái AI Engine thành công", aiBridgeService.isAIEngineRunning());
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return UUID.fromString(authentication.getName());
    }
}
