package com.ai_putway.service;

import com.ai_putway.client.AIEngineClient;
import com.ai_putway.dto.ChatRequest;
import com.ai_putway.dto.ChatResponse;
import com.ai_putway.dto.LocationSuggestionRequest;
import com.ai_putway.dto.LocationSuggestionResponse;
import com.auth_service.entity.UserAccount;
import com.auth_service.repository.UserRepository;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIBridgeService {

    private final AIEngineClient aiEngineClient;
    private final UserRepository userRepository;

    public LocationSuggestionResponse suggestLocations(LocationSuggestionRequest request, UUID userId) {
        requireActiveUser(userId);
        return aiEngineClient.suggestLocations(request);
    }

    public ChatResponse chat(String question, UUID userId, List<UUID> visibleWarehouseIds) {
        UserAccount user = requireActiveUser(userId);

        ChatRequest request = new ChatRequest();
        request.setQuestion(question);
        request.setUserId(user.getId());
        request.setRole(user.getRoleCodesCsv());
        request.setWarehouseIds(visibleWarehouseIds);

        return aiEngineClient.chat(request);
    }

    public boolean isAIEngineRunning() {
        return aiEngineClient.healthCheck();
    }

    private UserAccount requireActiveUser(UUID userId) {
        if (userId == null) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không xác định được người dùng hiện tại");
        }
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản đã bị khóa hoặc vô hiệu hóa");
        }
        return user;
    }
}
