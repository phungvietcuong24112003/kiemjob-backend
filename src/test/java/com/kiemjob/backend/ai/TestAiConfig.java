package com.kiemjob.backend.ai;

import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cấu hình Fake ChatClient cho profile test và môi trường CI/CD.
 * Đảm bảo mọi bài test khi chạy không bao giờ thực hiện HTTP call thực tế đến Groq API.
 */
@TestConfiguration
@Profile("test")
public class TestAiConfig {

    public static final String DEFAULT_MOCK_JSON_RESPONSE = """
            {
              "skillMatchScore": 85,
              "experienceScore": 80,
              "educationScore": 90,
              "strengths": ["Thành thạo Java 21 và Spring Framework", "Có kinh nghiệm thiết kế REST API"],
              "gaps": ["Chưa có kinh nghiệm thực tế với Kubernetes"],
              "summary": "Ứng viên có năng lực chuyên môn tốt, phù hợp cao với yêu cầu tuyển dụng."
            }
            """;

    @Bean("jsonChatClient")
    public ChatClient fakeJsonChatClient() {
        return createStubChatClient(DEFAULT_MOCK_JSON_RESPONSE);
    }

    @Bean("chatClient")
    @Primary
    public ChatClient fakeChatClient() {
        return createStubChatClient("Mô hình AI kiểm thử phản hồi thành công.");
    }

    /**
     * Tạo ChatClient stub hỗ trợ chuỗi gọi fluent API:
     * chatClient.prompt().system(...).user(...).call().content()
     */
    public static ChatClient createStubChatClient(String mockContent) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);

        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(mockContent);
        when(chatClient.prompt().system(any(String.class)).call().content())
                .thenReturn(mockContent);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn(mockContent);
        when(chatClient.prompt().call().content())
                .thenReturn(mockContent);

        return chatClient;
    }
}

