package com.kiemjob.backend.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiemjob.backend.ai.util.JsonResponseParser;
import com.kiemjob.backend.ai.util.PromptTemplates;
import com.kiemjob.backend.config.ai.AiAsyncConfig;
import com.kiemjob.backend.security.AiRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class SpringAiIntegrationTest {

    private PromptTemplates promptTemplates;
    private JsonResponseParser jsonParser;
    private AiRateLimiter rateLimiter;
    private ObjectMapper objectMapper;

    // Record mẫu để test JSON parsing
    record SampleCvResult(
            int skillMatchScore,
            int experienceScore,
            int educationScore,
            List<String> strengths,
            List<String> gaps,
            String summary
    ) {}

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        promptTemplates = new PromptTemplates();
        promptTemplates.init(); // nạp templates từ classpath:prompts/

        jsonParser = new JsonResponseParser(objectMapper);
        rateLimiter = new AiRateLimiter(3, 60); // giới hạn 3 req / 60s để test
    }

    // =================================================================
    // 1. Kiểm thử PromptTemplates
    // =================================================================

    @Test
    @DisplayName("PromptTemplates: Nạp template và render biến thành công")
    void testPromptTemplates_Render() {
        Map<String, Object> vars = Map.of(
                "jobTitle", "Backend Java Engineer",
                "jobLevel", "Senior",
                "jobDescription", "Phát triển hệ thống microservices",
                "jobRequirements", "5 năm kinh nghiệm Java, Spring Boot"
        );

        String rendered = promptTemplates.render("cv-scoring-system.st", vars);

        assertNotNull(rendered);
        assertTrue(rendered.contains("Backend Java Engineer"));
        assertTrue(rendered.contains("Senior"));
        assertTrue(rendered.contains("Phát triển hệ thống microservices"));
        assertTrue(rendered.contains("5 năm kinh nghiệm Java, Spring Boot"));
        assertFalse(rendered.contains("$jobTitle$"));
    }

    @Test
    @DisplayName("PromptTemplates: Cho phép gọi không cần đuôi .st và xử lý biến null an toàn")
    void testPromptTemplates_WithoutExtensionAndNullSafe() {
        Map<String, Object> vars = Map.of(
                "jobTitle", "DevOps Engineer"
        );

        String rendered = promptTemplates.render("cv-scoring-system", vars);

        assertNotNull(rendered);
        assertTrue(rendered.contains("DevOps Engineer"));
    }

    @Test
    @DisplayName("PromptTemplates: Ném lỗi khi template không tồn tại")
    void testPromptTemplates_NotFound() {
        assertThrows(IllegalArgumentException.class, () ->
                promptTemplates.getTemplate("non-existent-template.st")
        );
    }

    // =================================================================
    // 2. Kiểm thử JsonResponseParser
    // =================================================================

    @Test
    @DisplayName("JsonResponseParser: Bóc tách markdown ```json và parse thành công Object")
    void testJsonResponseParser_ParseMarkdownCodeBlock() throws JsonProcessingException {
        String rawWithMarkdown = """
                ```json
                {
                  "skillMatchScore": 90,
                  "experienceScore": 85,
                  "educationScore": 80,
                  "strengths": ["Java 21", "Spring AI"],
                  "gaps": ["Kubernetes"],
                  "summary": "Ứng viên rất tiềm năng."
                }
                ```
                """;

        SampleCvResult result = jsonParser.parse(rawWithMarkdown, SampleCvResult.class);

        assertNotNull(result);
        assertEquals(90, result.skillMatchScore());
        assertEquals(85, result.experienceScore());
        assertEquals(80, result.educationScore());
        assertEquals(List.of("Java 21", "Spring AI"), result.strengths());
        assertEquals(List.of("Kubernetes"), result.gaps());
        assertEquals("Ứng viên rất tiềm năng.", result.summary());
    }

    @Test
    @DisplayName("JsonResponseParser: Bóc tách JSON khi có văn bản dẫn nhập trước/sau")
    void testJsonResponseParser_ExtractFromSurroundingText() throws JsonProcessingException {
        String rawWithText = """
                Dưới đây là kết quả phân tích theo yêu cầu:
                {
                  "skillMatchScore": 75,
                  "experienceScore": 70,
                  "educationScore": 80,
                  "strengths": ["Giao tiếp tốt"],
                  "gaps": [],
                  "summary": "Đáp ứng cơ bản."
                }
                Chúc bạn một ngày làm việc hiệu quả!
                """;

        SampleCvResult result = jsonParser.parse(rawWithText, SampleCvResult.class);

        assertNotNull(result);
        assertEquals(75, result.skillMatchScore());
        assertEquals(List.of("Giao tiếp tốt"), result.strengths());
    }

    @Test
    @DisplayName("JsonResponseParser: Kiểm thử các helper clamp, nullSafe, truncate")
    void testJsonResponseParser_Helpers() {
        // clamp
        assertEquals(0, JsonResponseParser.clamp(null));
        assertEquals(0, JsonResponseParser.clamp(-10));
        assertEquals(100, JsonResponseParser.clamp(150));
        assertEquals(85, JsonResponseParser.clamp(85));

        // nullSafe
        assertEquals("", JsonResponseParser.nullSafe(null));
        assertEquals("abc", JsonResponseParser.nullSafe("abc"));

        // truncate
        assertEquals("Hello", JsonResponseParser.truncate("Hello", 10));
        assertTrue(JsonResponseParser.truncate("1234567890EXTRA", 10).contains("[nội dung đã được cắt ngắn]"));
    }

    // =================================================================
    // 3. Kiểm thử AiRateLimiter
    // =================================================================

    @Test
    @DisplayName("AiRateLimiter: Cho phép gọi trong ngưỡng và chặn khi vượt ngưỡng")
    void testAiRateLimiter_TryAcquireAndCheckRateLimit() {
        String user = "candidate_01@example.com";

        // 3 lượt đầu tiên thành công (maxRequestsPerMinute = 3)
        assertTrue(rateLimiter.tryAcquire(user));
        assertTrue(rateLimiter.tryAcquire(user));
        assertTrue(rateLimiter.tryAcquire(user));

        // Lượt thứ 4 phải bị từ chối
        assertFalse(rateLimiter.tryAcquire(user));

        // Gọi checkRateLimit phải ném 429
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                rateLimiter.checkRateLimit(user)
        );
        assertEquals(429, ex.getStatusCode().value());

        // Reset bộ đếm user
        rateLimiter.reset(user);
        assertTrue(rateLimiter.tryAcquire(user));
    }

    // =================================================================
    // 4. Kiểm thử AiAsyncConfig (aiTaskExecutor)
    // =================================================================

    @Test
    @DisplayName("AiAsyncConfig: Cấu hình thread pool đúng thông số và từ chối khi queue đầy (AbortPolicy)")
    void testAiAsyncConfig_ThreadPoolParameters() {
        AiAsyncConfig asyncConfig = new AiAsyncConfig();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncConfig.aiTaskExecutor();

        assertEquals(2, executor.getCorePoolSize());
        assertEquals(5, executor.getMaxPoolSize());
        assertEquals(100, executor.getQueueCapacity());
        assertTrue(executor.getThreadNamePrefix().startsWith("ai-task-"));

        executor.destroy();
    }

    // =================================================================
    // 5. Kiểm thử Fake ChatClient Stub (Không gọi Groq thật)
    // =================================================================

    @Test
    @DisplayName("TestAiConfig: Fake ChatClient trả về JSON giả lập mà không thực hiện HTTP call")
    void testFakeChatClient_ReturnsMockResponse() {
        ChatClient fakeClient = TestAiConfig.createStubChatClient(TestAiConfig.DEFAULT_MOCK_JSON_RESPONSE);

        String response = fakeClient.prompt()
                .system("You are an assistant")
                .user("Analyze this CV")
                .call()
                .content();

        assertNotNull(response);
        assertTrue(response.contains("skillMatchScore"));
        assertTrue(response.contains("85"));
    }
}

