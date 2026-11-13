package com.kiemjob.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiemjob.backend.ai.util.AiTextSanitizer;
import com.kiemjob.backend.ai.util.JsonResponseParser;
import com.kiemjob.backend.ai.util.PdfTextExtractor;
import com.kiemjob.backend.ai.util.PromptTemplates;
import com.kiemjob.backend.config.ai.GroqProperties;
import com.kiemjob.backend.dto.application.ApplicationAiScoreResponse;
import com.kiemjob.backend.entity.*;
import com.kiemjob.backend.repository.*;
import com.kiemjob.backend.security.AiRateLimiter;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.ai.GroqClient;
import com.kiemjob.backend.service.ai.cache.ApplicationAiScoreCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationAiScoringTest {

    @Mock
    private CvAnalysisRepository analysisRepository;
    @Mock
    private CvRepository cvRepository;
    @Mock
    private CvCareerObjectiveRepository cvCareerObjectiveRepository;
    @Mock
    private CvSkillGroupRepository cvSkillGroupRepository;
    @Mock
    private CvWorkExperienceRepository cvWorkExperienceRepository;
    @Mock
    private CvEducationRepository cvEducationRepository;
    @Mock
    private CvProjectRepository cvProjectRepository;
    @Mock
    private CvCertificateRepository cvCertificateRepository;
    @Mock
    private CvAwardRepository cvAwardRepository;
    @Mock
    private CvHobbyRepository cvHobbyRepository;
    @Mock
    private CvMoreRepository cvMoreRepository;
    @Mock
    private CandidateLinkRepository candidateLinkRepository;
    @Mock
    private CvPdfFileRepository cvPdfFileRepository;
    @Mock
    private JobPostRepository jobPostRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private JobDetailRepository jobDetailRepository;
    @Mock
    private JobPostSpecializationRepository jobPostSpecializationRepository;
    @Mock
    private JobSkillRepository jobSkillRepository;
    @Mock
    private GroqProperties groqProperties;
    @Mock
    private S3Client s3Client;
    @Mock
    private PdfTextExtractor pdfTextExtractor;
    @Mock
    private PromptTemplates promptTemplates;
    @Mock
    private GroqClient groqClient;
    @Mock
    private AiRateLimiter aiRateLimiter;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private CvOwnershipService cvOwnershipService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private JsonResponseParser jsonResponseParser;
    private ApplicationAiScoreCache aiScoreCache;
    private CvAnalysisService cvAnalysisService;

    private Application sampleApp;
    private JobPost sampleJob;
    private Cv sampleCv;
    private Company sampleCompany;

    @BeforeEach
    void setUp() {
        jsonResponseParser = new JsonResponseParser(objectMapper);
        aiScoreCache = new ApplicationAiScoreCache(); // Dùng cache thực tế trong bộ nhớ để test hành vi cache

        cvAnalysisService = new CvAnalysisService(
                analysisRepository,
                cvRepository,
                cvCareerObjectiveRepository,
                cvSkillGroupRepository,
                cvWorkExperienceRepository,
                cvEducationRepository,
                cvProjectRepository,
                cvCertificateRepository,
                cvAwardRepository,
                cvHobbyRepository,
                cvMoreRepository,
                candidateLinkRepository,
                cvPdfFileRepository,
                jobPostRepository,
                applicationRepository,
                jobDetailRepository,
                jobPostSpecializationRepository,
                jobSkillRepository,
                aiScoreCache,
                groqProperties,
                s3Client,
                pdfTextExtractor,
                promptTemplates,
                groqClient,
                jsonResponseParser,
                aiRateLimiter,
                currentUserService,
                cvOwnershipService,
                objectMapper
        );

        sampleCompany = new Company();
        ReflectionTestUtils.setField(sampleCompany, "id", 100L);

        sampleJob = new JobPost();
        sampleJob.setId(10L);
        sampleJob.setTitle("Senior Java Developer");
        sampleJob.setEducationalRequirement("Đại học chuyên ngành CNTT");
        sampleJob.setCompany(sampleCompany);

        sampleCv = new Cv();
        sampleCv.setId(100L);
        sampleCv.setTitle("CV Nguyễn Văn A");

        sampleApp = new Application();
        sampleApp.setId(500L);
        sampleApp.setJobPost(sampleJob);
        sampleApp.setCv(sampleCv);

        // Mặc định cho phép admin để bypass kiểm tra quyền recruiter
        lenient().when(currentUserService.isAdmin()).thenReturn(true);
    }

    @Test
    @DisplayName("Tính điểm tổng do code tính với bài post có yêu cầu học vấn (50% skill, 35% exp, 15% edu)")
    void scoreApplicationAgainstJob_withEducationRequirement_calculatesCorrectWeights() {
        CvSkillGroup sg = new CvSkillGroup();
        sg.setTitle("Backend");
        sg.setContent("Java, Spring Boot, PostgreSQL, Docker, Microservices với 5 năm kinh nghiệm thực tế.");
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(100L)).thenReturn(Optional.empty());
        when(cvSkillGroupRepository.findAllByCvIdOrderByDisplayOrderAscIdAsc(100L)).thenReturn(List.of(sg));

        when(applicationRepository.findById(500L)).thenReturn(Optional.of(sampleApp));

        when(promptTemplates.render(eq("cv-vs-job-system"), any(java.util.Map.class))).thenReturn("System prompt");
        when(promptTemplates.render(eq("cv-vs-job-user"), any(java.util.Map.class))).thenReturn("User prompt");
        when(groqProperties.getModel()).thenReturn("llama-3.3-70b-versatile");

        // AI trả về: skill=80, exp=70, edu=60; không thiếu kỹ năng
        String aiJson = """
                {
                  "skillMatchScore": 80,
                  "experienceScore": 70,
                  "educationScore": 60,
                  "matchedSkills": ["Java", "Spring Boot"],
                  "missingSkills": [],
                  "strengths": [{"point": "Vững Spring Boot", "evidenceQuote": "Spring Boot 5 năm"}],
                  "gaps": [],
                  "summary": "Ứng viên phù hợp vị trí."
                }
                """;
        when(groqClient.generateJson(any(), any())).thenReturn(aiJson);

        ApplicationAiScoreResponse result = cvAnalysisService.scoreApplicationAgainstJob(500L);

        // Expected: (80 * 0.50) + (70 * 0.35) + (60 * 0.15) = 40 + 24.5 + 9 = 73.5 -> round = 74
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getOverallScore()).isEqualTo(74);
        assertThat(result.getSkillScore()).isEqualTo(80);
        assertThat(result.getExperienceScore()).isEqualTo(70);
        assertThat(result.getEducationScore()).isEqualTo(60);
        assertThat(result.isCached()).isFalse();

        // Lần gọi thứ 2 với cùng dữ liệu -> lấy từ cache trong bộ nhớ (cached = true, không gọi lại Groq)
        ApplicationAiScoreResponse cachedResult = cvAnalysisService.scoreApplicationAgainstJob(500L);
        assertThat(cachedResult.isCached()).isTrue();
        assertThat(cachedResult.getOverallScore()).isEqualTo(74);
        verify(groqClient, times(1)).generateJson(any(), any());
    }

    @Test
    @DisplayName("Bài post không yêu cầu học vấn: phân bổ lại trọng số cho skill và exp (không phạt oan học vấn)")
    void scoreApplicationAgainstJob_noEducationRequirement_redistributesWeights() {
        sampleJob.setEducationalRequirement("Không yêu cầu");

        CvSkillGroup sg = new CvSkillGroup();
        sg.setTitle("Backend");
        sg.setContent("Java, Spring Boot, PostgreSQL, Docker, Microservices với 5 năm kinh nghiệm thực tế.");
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(100L)).thenReturn(Optional.empty());
        when(cvSkillGroupRepository.findAllByCvIdOrderByDisplayOrderAscIdAsc(100L)).thenReturn(List.of(sg));

        when(applicationRepository.findById(500L)).thenReturn(Optional.of(sampleApp));

        when(promptTemplates.render(eq("cv-vs-job-system"), any(java.util.Map.class))).thenReturn("System prompt");
        when(promptTemplates.render(eq("cv-vs-job-user"), any(java.util.Map.class))).thenReturn("User prompt");
        when(groqProperties.getModel()).thenReturn("llama-3.3-70b-versatile");

        // AI trả về: skill=90, exp=80, edu=null
        String aiJson = """
                {
                  "skillMatchScore": 90,
                  "experienceScore": 80,
                  "educationScore": null,
                  "matchedSkills": ["Java", "Spring Boot"],
                  "missingSkills": [],
                  "strengths": [{"point": "Chuyên môn cao", "evidenceQuote": "Java 5 năm"}],
                  "gaps": [],
                  "summary": "Ứng viên rất tốt."
                }
                """;
        when(groqClient.generateJson(any(), any())).thenReturn(aiJson);

        ApplicationAiScoreResponse result = cvAnalysisService.scoreApplicationAgainstJob(500L);

        // Expected: weights: skill = 0.50/0.85 = ~0.5882; exp = 0.35/0.85 = ~0.4118
        // 90 * 0.5882 + 80 * 0.4118 = 52.94 + 32.94 = 85.88 -> round = 86
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getOverallScore()).isEqualTo(86);
    }

    @Test
    @DisplayName("CV không trích xuất được text (PDF scan/ảnh hoặc rỗng) -> NO_TEXT (không gán 0 điểm, không cache)")
    void scoreApplicationAgainstJob_noText_setsNoTextStatus() {
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(100L)).thenReturn(Optional.empty());
        when(applicationRepository.findById(500L)).thenReturn(Optional.of(sampleApp));

        ApplicationAiScoreResponse result = cvAnalysisService.scoreApplicationAgainstJob(500L);

        assertThat(result.getStatus()).isEqualTo("NO_TEXT");
        assertThat(result.getOverallScore()).isNull();
        assertThat(result.isCached()).isFalse();
        assertThat(aiScoreCache.size()).isEqualTo(0);
        verifyNoInteractions(groqClient);
    }

    @Test
    @DisplayName("AI trả về JSON hỏng -> FAILED (không gán 0 điểm, không cache)")
    void scoreApplicationAgainstJob_malformedJson_setsFailedStatus() {
        CvSkillGroup sg = new CvSkillGroup();
        sg.setTitle("Backend");
        sg.setContent("Java, Spring Boot, PostgreSQL, Docker, Microservices với 5 năm kinh nghiệm thực tế.");
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(100L)).thenReturn(Optional.empty());
        when(cvSkillGroupRepository.findAllByCvIdOrderByDisplayOrderAscIdAsc(100L)).thenReturn(List.of(sg));

        when(applicationRepository.findById(500L)).thenReturn(Optional.of(sampleApp));

        when(promptTemplates.render(eq("cv-vs-job-system"), any(java.util.Map.class))).thenReturn("System prompt");
        when(promptTemplates.render(eq("cv-vs-job-user"), any(java.util.Map.class))).thenReturn("User prompt");
        when(groqClient.generateJson(any(), any())).thenReturn("INVALID NON JSON TEXT HERE");

        ApplicationAiScoreResponse result = cvAnalysisService.scoreApplicationAgainstJob(500L);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getOverallScore()).isNull();
        assertThat(result.isCached()).isFalse();
        assertThat(aiScoreCache.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Kiểm tra che PII và bảo toàn đoạn văn khi strip HTML")
    void piiMaskingAndHtmlStripping_worksCorrectly() {
        String originalCv = "Ứng viên: Nguyen Van A, email: nguyenvana@gmail.com, sđt: 0987654321, tai Ha Noi.";
        String masked = AiTextSanitizer.maskPii(originalCv);
        assertThat(masked).doesNotContain("nguyenvana@gmail.com");
        assertThat(masked).doesNotContain("0987654321");
        assertThat(masked).contains("[EMAIL]");
        assertThat(masked).contains("[PHONE]");

        String htmlJob = "<p>Mô tả 1</p><p>Mô tả 2<br/>Dòng tiếp theo</p><ul><li>Ý 1</li><li>Ý 2</li></ul>";
        String clean = AiTextSanitizer.stripHtmlPreservingBreaks(htmlJob);
        assertThat(clean).doesNotContain("<p>").doesNotContain("</p>").doesNotContain("<li>");
        assertThat(clean).contains("\n");
    }
}
