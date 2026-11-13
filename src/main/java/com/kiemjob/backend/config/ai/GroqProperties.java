package com.kiemjob.backend.config.ai;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "groq")
public class GroqProperties {

    /**
     * Groq API Key (bắt buộc cấu hình khi chạy Production)
     */
    private String apiKey;

    /**
     * Base URL của Groq API (mặc định https://api.groq.com/openai/v1)
     */
    private String baseUrl = "https://api.groq.com/openai/v1";

    /**
     * Tên mô hình Groq.
     * LƯU Ý: Vui lòng kiểm tra https://console.groq.com/docs/models để cập nhật mô hình còn hiệu lực.
     */
    private String model = "openai/gpt-oss-120b";

    /**
     * Timeout đọc dữ liệu và kết nối (giây)
     */
    private int timeoutSeconds = 90;

    /**
     * Số lần thử lại tối đa khi gặp lỗi 429 hoặc 5xx
     */
    private int maxRetries = 3;

    private final Environment environment;

    public GroqProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd) {
            if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("your_groq_api_key")) {
                throw new IllegalStateException(
                        "LỖI KHỞI ĐỘNG PRODUCTION: Biến môi trường GROQ_API_KEY (hoặc property groq.api-key) "
                                + "chưa được thiết lập hoặc còn để giá trị mẫu! Bắt buộc phải cung cấp API key hợp lệ.");
            }
            if (model == null || model.trim().isEmpty()) {
                throw new IllegalStateException(
                        "LỖI KHỞI ĐỘNG PRODUCTION: Biến môi trường GROQ_MODEL (hoặc property groq.model) "
                                + "chưa được cấu hình.");
            }
            log.info("Khởi động môi trường Production với Groq Model: {}", model);
        } else {
            if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("your_groq_api_key")) {
                log.warn("CẢNH BÁO [DEV]: Chưa cấu hình GROQ_API_KEY. Tính năng kiểm duyệt AI sẽ báo lỗi nếu được gọi thực tế.");
            } else {
                log.info("Groq AI được khởi tạo thành công trên môi trường Non-Prod với model: {}", model);
            }
        }
    }
}

