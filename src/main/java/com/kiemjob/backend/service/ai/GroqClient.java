package com.kiemjob.backend.service.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiemjob.backend.config.ai.GroqProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Slf4j
@Service
public class GroqClient {

    private final RestClient groqRestClient;
    private final GroqProperties groqProperties;
    private final ObjectMapper objectMapper;

    public GroqClient(
            @Qualifier("groqRestClient") RestClient groqRestClient,
            GroqProperties groqProperties,
            ObjectMapper objectMapper
    ) {
        this.groqRestClient = groqRestClient;
        this.groqProperties = groqProperties;
        this.objectMapper = objectMapper;
    }

    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            double temperature,
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {}

    public record ChatMessage(
            String role,
            String content
    ) {}

    public record ResponseFormat(
            String type
    ) {}

    /**
     * Gửi yêu cầu sinh phản hồi định dạng JSON đến Groq API sử dụng model mặc định.
     */
    public String generateJson(String systemPrompt, String userPrompt) {
        return generateJson(systemPrompt, userPrompt, groqProperties.getModel());
    }

    /**
     * Gửi yêu cầu sinh phản hồi định dạng JSON đến Groq API với tùy chọn ghi đè model.
     */
    public String generateJson(String systemPrompt, String userPrompt, String modelOverride) {
        String apiKey = groqProperties.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("your_groq_api_key")) {
            throw new IllegalStateException("GROQ_API_KEY chưa được cấu hình hợp lệ.");
        }

        String effectiveModel = (modelOverride != null && !modelOverride.isBlank())
                ? modelOverride.trim()
                : groqProperties.getModel();

        ChatRequest requestBody = new ChatRequest(
                effectiveModel,
                List.of(
                        new ChatMessage("system", systemPrompt),
                        new ChatMessage("user", userPrompt)
                ),
                0.0,
                new ResponseFormat("json_object")
        );

        int maxRetries = Math.max(1, groqProperties.getMaxRetries());

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String responseBody = groqRestClient.post()
                        .uri("/chat/completions")
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                return extractContentFromResponse(responseBody);

            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();

                if (status == 429) {
                    if (attempt == maxRetries) {
                        log.error("Groq API đạt giới hạn tần suất (429) và đã hết số lần thử lại ({})", maxRetries);
                        throw new RuntimeException("Groq API vượt quá giới hạn tần suất (429)", ex);
                    }
                    long waitMs = parseRetryAfter(ex.getResponseHeaders());
                    if (waitMs < 0) {
                        waitMs = (long) Math.pow(2, attempt) * 1000L;
                    }
                    log.warn("Groq API trả về 429 (Rate limit). Sẽ thử lại lần {}/{} sau {} ms", attempt + 1, maxRetries, waitMs);
                    sleep(waitMs);
                    continue;
                }

                if (status >= 500 && status < 600) {
                    if (attempt == maxRetries) {
                        log.error("Groq API lỗi máy chủ ({}) sau {} lần thử lại", status, maxRetries);
                        throw new RuntimeException("Groq API lỗi máy chủ (" + status + ")", ex);
                    }
                    long waitMs = (long) Math.pow(2, attempt) * 1000L;
                    log.warn("Groq API trả về lỗi {} từ máy chủ. Thử lại lần {}/{} sau {} ms", status, attempt + 1, maxRetries, waitMs);
                    sleep(waitMs);
                    continue;
                }

                String errorDetail = extractGroqErrorMessage(ex);
                log.error("Groq API trả về lỗi không thể thử lại: HTTP {}{}", status, errorDetail);
                throw new RuntimeException("Lỗi gọi Groq API: HTTP " + status + errorDetail, ex);

            } catch (ResourceAccessException ex) {
                if (attempt == maxRetries) {
                    log.error("Groq API timeout hoặc lỗi mạng sau {} lần thử lại", maxRetries);
                    throw new RuntimeException("Groq API timeout hoặc lỗi mạng", ex);
                }
                long waitMs = (long) Math.pow(2, attempt) * 1000L;
                log.warn("Groq API timeout hoặc lỗi kết nối. Thử lại lần {}/{} sau {} ms", attempt + 1, maxRetries, waitMs);
                sleep(waitMs);
            }
        }

        throw new RuntimeException("Không nhận được phản hồi từ Groq API sau " + maxRetries + " lần thử.");
    }

    private String extractContentFromResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("Phản hồi từ Groq API trống.");
        }
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode choices = rootNode.path("choices");
            if (choices.isEmpty() || !choices.has(0)) {
                throw new IllegalStateException("Phản hồi từ Groq API không có trường 'choices'.");
            }
            JsonNode message = choices.get(0).path("message");
            JsonNode content = message.path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("Phản hồi từ Groq API không có trường 'message.content'.");
            }
            return content.asText();
        } catch (Exception ex) {
            log.error("Lỗi parse phản hồi JSON từ Groq API: {}", ex.getMessage());
            throw new RuntimeException("Lỗi xử lý phản hồi từ Groq API: " + ex.getMessage(), ex);
        }
    }

    private long parseRetryAfter(HttpHeaders headers) {
        if (headers == null) return -1;
        String header = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (header == null || header.isBlank()) return -1;
        try {
            long seconds = Long.parseLong(header.trim());
            return Math.min(seconds * 1000L, 15000L);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String extractGroqErrorMessage(RestClientResponseException ex) {
        try {
            String body = ex.getResponseBodyAsString();
            if (body != null && !body.isBlank()) {
                JsonNode errNode = objectMapper.readTree(body).path("error");
                String message = errNode.path("message").asText();
                if (message != null && !message.isBlank()) {
                    return " - " + message;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void sleep(long waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Quá trình chờ thử lại gọi Groq API bị gián đoạn", ie);
        }
    }
}
