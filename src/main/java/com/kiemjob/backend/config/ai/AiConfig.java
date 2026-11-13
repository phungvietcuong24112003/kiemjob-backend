package com.kiemjob.backend.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Cấu hình Spring AI 2.0 ChatClient kết nối tới Groq qua OpenAI Starter.
 * Cung cấp 2 ChatClient:
 * - jsonChatClient: Chuyên dùng cho các tác vụ trích xuất/chấm điểm cấu trúc (temperature ~0.1, JSON mode).
 * - chatClient: Dùng cho đàm thoại, sinh nội dung thông thường (temperature ~0.7, maxTokens 1000).
 */
@Configuration
@Profile("!test")
public class AiConfig {

    @Value("${spring.ai.openai.chat.options.model:openai/gpt-oss-120b}")
    private String defaultModel;

    @Bean("jsonChatClient")
    @ConditionalOnMissingBean(name = "jsonChatClient")
    public ChatClient jsonChatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(defaultModel)
                        .temperature(0.1)
                        .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                                .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                                .build()))
                .defaultSystem("""
                        Bạn là trợ lý AI chuyên nghiệp phân tích tuyển dụng và hồ sơ ứng viên tại Việt Nam.
                        QUAN TRỌNG: Chỉ trả về JSON hợp lệ, không thêm bất kỳ văn bản nào khác.
                        Không dùng markdown, không giải thích, chỉ trả về JSON thuần.
                        """)
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "chatClient")
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(defaultModel)
                        .temperature(0.7)
                        .maxTokens(1000))
                .build();
    }
}
