package com.kiemjob.backend.ai.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tiện ích bóc tách và phân tích cú pháp phản hồi JSON từ AI Model.
 * Xử lý các trường hợp AI trả về markdown code blocks (```json ... ```)
 * hoặc kèm theo text dẫn nhập, đảm bảo trích xuất chính xác payload JSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * Bóc tách JSON thuần từ phản hồi thô của AI model:
     * - Loại bỏ markdown code fences (```json, ```)
     * - Trích xuất đoạn ký tự từ '{' đầu tiên đến '}' cuối cùng (cho JSON Object)
     *   hoặc từ '[' đến ']' (cho JSON Array).
     */
    public String cleanJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Nội dung AI phản hồi rỗng hoặc null");
        }

        String text = raw.trim();

        // 1. Loại bỏ markdown code blocks
        if (text.startsWith("```")) {
            text = text.replaceAll("(?s)^```(?:json)?\\s*", "")
                       .replaceAll("(?s)```\\s*$", "")
                       .trim();
        }

        // 2. Tìm điểm bắt đầu và kết thúc của JSON Object {...}
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1).trim();
        }

        // 3. Dự phòng cho JSON Array [...]
        int firstBracket = text.indexOf('[');
        int lastBracket = text.lastIndexOf(']');
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            return text.substring(firstBracket, lastBracket + 1).trim();
        }

        return text;
    }

    /**
     * Parse chuỗi phản hồi thô thành Object thuộc kiểu clazz.
     */
    public <T> T parse(String raw, Class<T> clazz) throws JsonProcessingException {
        String clean = cleanJson(raw);
        return objectMapper.readValue(clean, clazz);
    }

    /**
     * Parse chuỗi phản hồi thô với Generic Type (TypeReference).
     */
    public <T> T parse(String raw, TypeReference<T> typeRef) throws JsonProcessingException {
        String clean = cleanJson(raw);
        return objectMapper.readValue(clean, typeRef);
    }

    // =================================================================
    // Helper Methods
    // =================================================================

    /**
     * Giới hạn điểm số trong khoảng [0, 100].
     * Nếu value null thì mặc định trả về 0.
     */
    public static int clamp(Integer value) {
        return clamp(value, 0, 100);
    }

    /**
     * Giới hạn giá trị trong khoảng [min, max].
     */
    public static int clamp(Integer value, int min, int max) {
        if (value == null) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Chuyển đổi an toàn chuỗi null thành chuỗi rỗng.
     */
    public static String nullSafe(String s) {
        return s != null ? s : "";
    }

    /**
     * Cắt ngắn văn bản nếu vượt quá độ dài tối đa cho phép để tránh tràn context token.
     */
    public static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[nội dung đã được cắt ngắn]";
    }
}

