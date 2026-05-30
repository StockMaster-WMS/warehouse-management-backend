package com.ai_service.controller;

import com.ai_service.dto.AiProviderKeyStatusResponse;
import com.ai_service.dto.UpdateAiProviderKeyRequest;
import com.ai_service.service.provider.AiProviderConfigService;
import com.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/config")
@RequiredArgsConstructor
@Tag(name = "AI Config", description = "Cấu hình khóa API cho trợ lý AI")
@PreAuthorize("hasAuthority('ADMIN')")
public class AiConfigController {

    private final AiProviderConfigService configService;

    @GetMapping("/cloud-key")
    @Operation(summary = "Lấy trạng thái API key AI đám mây")
    public ApiResponse<AiProviderKeyStatusResponse> getCloudKeyStatus() {
        return ApiResponse.success("Lấy trạng thái API key AI thành công",
                configService.getGeminiKeyStatus());
    }

    @GetMapping("/providers")
    @Operation(summary = "Lấy trạng thái API key theo provider")
    public ApiResponse<List<AiProviderKeyStatusResponse>> getProviderKeyStatuses() {
        return ApiResponse.success("Lấy trạng thái API key AI thành công",
                configService.getProviderKeyStatuses());
    }

    @GetMapping("/providers/{provider}/key")
    @Operation(summary = "Lấy trạng thái API key của một provider")
    public ApiResponse<AiProviderKeyStatusResponse> getProviderKeyStatus(@PathVariable String provider) {
        return ApiResponse.success("Lấy trạng thái API key AI thành công",
                configService.getKeyStatus(provider));
    }

    @PutMapping("/providers/{provider}/key")
    @Operation(summary = "Cập nhật API key của một provider")
    public ApiResponse<AiProviderKeyStatusResponse> updateProviderKey(
            @PathVariable String provider,
            @Valid @RequestBody UpdateAiProviderKeyRequest request) {
        return ApiResponse.success("Cập nhật API key AI thành công",
                configService.updateKey(provider, request));
    }

    @DeleteMapping("/providers/{provider}/key")
    @Operation(summary = "Xóa API key của một provider")
    public ApiResponse<AiProviderKeyStatusResponse> clearProviderKey(@PathVariable String provider) {
        return ApiResponse.success("Xóa API key AI thành công",
                configService.clearKey(provider));
    }

    @PutMapping("/cloud-key")
    @Operation(summary = "Cập nhật API key AI đám mây")
    public ApiResponse<AiProviderKeyStatusResponse> updateCloudKey(
            @Valid @RequestBody UpdateAiProviderKeyRequest request) {
        return ApiResponse.success("Cập nhật API key AI thành công",
                configService.updateGeminiKey(request));
    }

    @DeleteMapping("/cloud-key")
    @Operation(summary = "Xóa API key AI đám mây")
    public ApiResponse<AiProviderKeyStatusResponse> clearCloudKey() {
        return ApiResponse.success("Xóa API key AI thành công",
                configService.clearGeminiKey());
    }
}
