package com.ai_putway.client;

import com.ai_putway.dto.ChatRequest;
import com.ai_putway.dto.ChatResponse;
import com.ai_putway.dto.LocationSuggestionRequest;
import com.ai_putway.dto.LocationSuggestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AIEngineClient {

    @Value("${ai.engine.url:}")
    private String aiEngineUrl;

    @Qualifier("aiRestTemplate")
    private final RestTemplate restTemplate;

    public LocationSuggestionResponse suggestLocations(LocationSuggestionRequest request) {
        if (aiEngineUrl == null || aiEngineUrl.isBlank()) {
            LocationSuggestionResponse errorResponse = new LocationSuggestionResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError("Chưa cấu hình AI Engine URL");
            return errorResponse;
        }

        String url = aiEngineUrl + "/suggest-locations";
        log.info("Calling AI Engine: POST {}", url);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<LocationSuggestionRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<LocationSuggestionResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, LocationSuggestionResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("AI Engine suggest location error: {}", e.getMessage());
            LocationSuggestionResponse errorResponse = new LocationSuggestionResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError("Không thể kết nối đến AI Engine: " + e.getMessage());
            return errorResponse;
        }
    }

    public ChatResponse chat(ChatRequest request) {
        if (aiEngineUrl == null || aiEngineUrl.isBlank()) {
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError("Chưa cấu hình AI Engine URL");
            return errorResponse;
        }

        String url = aiEngineUrl + "/chat";
        log.info("Calling AI Chat: POST {}", url);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ChatResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, ChatResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("AI Chat error: {}", e.getMessage());
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError("Không thể kết nối đến AI Engine: " + e.getMessage());
            return errorResponse;
        }
    }

    public boolean healthCheck() {
        if (aiEngineUrl == null || aiEngineUrl.isBlank()) {
            return false;
        }
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(aiEngineUrl + "/health", Map.class);
            return response.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(200));
        } catch (Exception e) {
            return false;
        }
    }
}
