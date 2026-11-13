package com.kiemjob.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kiemjob.backend.ai.util.JsonResponseParser;
import com.kiemjob.backend.ai.util.PdfTextExtractor;
import com.kiemjob.backend.ai.util.PromptTemplates;
import com.kiemjob.backend.dto.cv.CvAnalysisResponse;
import com.kiemjob.backend.dto.cv.CvScoreRequest;
import com.kiemjob.backend.entity.*;
import com.kiemjob.backend.repository.*;
import com.kiemjob.backend.security.AiRateLimiter;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.ai.GroqClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CvAnalysisServiceTest {

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
    private com.kiemjob.backend.service.ai.cache.ApplicationAiScoreCache aiScoreCache;
    @Mock
    private com.kiemjob.backend.config.ai.GroqProperties groqProperties;
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

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private JsonResponseParser jsonResponseParser;
    private CvAnalysisService cvAnalysisService;

    private static final Long CV_ID = 1L;
    private static final Long CANDIDATE_ID = 10L;

    private Cv sampleCv;
    private Account sampleAccount;
    private CvPdfFile samplePdfFile;

    private static final String VALID_AI_RESPONSE = """
            {
              "detectedField": "Công nghệ thông tin - Backend",
              "detectedLevel": "junior",
              "overallScore": 82,
              "overallLevel": "good",
              "summary": "Ứng viên có nền tảng kỹ thuật tốt về Java và Spring Boot.",
              "topPriorities": [
                "Bổ sung số liệu định lượng cho các dự án",
                "Nâng cao chứng chỉ chuyên ngành"
              ],
              "sections": [
                {
                  "category": "skills",
                  "title": "Kỹ năng chuyên môn",
                  "found": ["Java", "Spring Boot", "PostgreSQL"],
                  "score": 85,
                  "level": "good",
                  "comment": "Kỹ năng cốt lõi phù hợp với vị trí Backend.",
                  "strengths": ["Thành thạo Java và Spring Boot"],
                  "improvements": ["Nên bổ sung kiến thức về Docker và CI/CD"]
                },
                {
                  "category": "experience",
                  "title": "Kinh nghiệm làm việc",
                  "found": ["Lập trình viên Java tại Tech Corp"],
                  "score": 75,
                  "level": "good",
                  "comment": "Có kinh nghiệm thực tế tốt.",
                  "strengths": ["Kinh nghiệm làm việc thực tế"],
                  "improvements": ["Cần thêm số liệu KPI cụ thể"]
                },
                {
                  "category": "education",
                  "title": "Học vấn",
                  "found": ["Đại học Bách Khoa - CNTT"],
                  "score": 90,
                  "level": "excellent",
                  "comment": "Đúng chuyên ngành đào tạo.",
                  "strengths": ["Tốt nghiệp đúng chuyên ngành"],
                  "improvements": []
                }
              ],
              "bonuses": [
                {
                  "title": "Chứng chỉ TOEIC 800",
                  "detail": "Khả năng đọc hiểu tài liệu tiếng Anh tốt",
                  "impact": "high"
                }
              ],
              "missingSections": [
                {
                  "title": "Link GitHub",
                  "reason": "Ngành CNTT cần xem source code mẫu",
                  "suggestion": "Bổ sung link GitHub cá nhân vào phần liên kết"
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        jsonResponseParser = new JsonResponseParser(objectMapper);
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

        Candidate candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", CANDIDATE_ID);

        sampleCv = Cv.builder()
                .candidate(candidate)
                .title("Lập trình viên Backend Java")
                .build();
        ReflectionTestUtils.setField(sampleCv, "id", CV_ID);

        sampleAccount = new Account();
        sampleAccount.setEmail("candidate@kiemjob.com");

        samplePdfFile = CvPdfFile.builder()
                .cvId(CV_ID)
                .bucketName("kiemjob-bucket")
                .objectKey("cvs/1/sample.pdf")
                .fileName("sample.pdf")
                .isCurrent(true)
                .build();
    }

    @Test
    @DisplayName("1. Chấm điểm thành công: Gọi AI, tính đúng qualityScore bằng overallScore, lưu và trả về phản hồi")
    void testScoreCv_Success() {
        // Arrange
        doNothing().when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);
        when(currentUserService.currentAccount()).thenReturn(Optional.of(sampleAccount));
        doNothing().when(aiRateLimiter).checkRateLimit("candidate@kiemjob.com");
        when(cvRepository.findById(CV_ID)).thenReturn(Optional.of(sampleCv));
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(CV_ID)).thenReturn(Optional.empty());

        when(cvCareerObjectiveRepository.findByCvId(CV_ID)).thenReturn(Optional.empty());
        when(cvSkillGroupRepository.findAllByCvIdOrderByDisplayOrderAscIdAsc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvWorkExperienceRepository.findByCv_IdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvEducationRepository.findByCv_IdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvProjectRepository.findAllByCvIdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvCertificateRepository.findAllByCvIdOrderByCertificateDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvAwardRepository.findAllByCvIdOrderByAwardDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvHobbyRepository.findAllByCvIdOrderByIdAsc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvMoreRepository.findByCv_IdOrderByIdAsc(CV_ID)).thenReturn(Collections.emptyList());
        when(candidateLinkRepository.findAllByCandidate_IdOrderByIdAsc(CANDIDATE_ID)).thenReturn(Collections.emptyList());

        when(promptTemplates.render(eq("cv-scoring-system"), any(java.util.Map.class))).thenReturn("System Prompt");
        when(promptTemplates.render(eq("cv-scoring-user"), any(java.util.Map.class))).thenReturn("User Prompt");

        when(groqClient.generateJson(anyString(), anyString())).thenReturn(VALID_AI_RESPONSE);

        when(analysisRepository.save(any(CvAnalysis.class))).thenAnswer(invocation -> {
            CvAnalysis entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 100L);
            return entity;
        });

        CvScoreRequest request = new CvScoreRequest();
        request.setTargetJobTitle("Backend Developer");

        // Act
        CvAnalysisResponse response = cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, request);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(CV_ID, response.getCvId());

        // qualityScore đúng bằng overallScore = 82
        assertEquals(new BigDecimal("82"), response.getQualityScore());
        assertEquals("manual", response.getDataSource());
        assertNotNull(response.getSuggestions());
        assertTrue(response.getSuggestions().contains("Ngành nghề:"));
        assertTrue(response.getSuggestions().contains("Tổng quan:"));
        assertTrue(response.getSuggestions().contains("Nên sửa trước:"));
        assertTrue(response.getSuggestions().contains("Kỹ năng chuyên môn"));
        assertTrue(response.getSuggestions().contains("Điểm cộng:"));
        assertTrue(response.getSuggestions().contains("Mục nên bổ sung:"));

        ArgumentCaptor<CvAnalysis> captor = ArgumentCaptor.forClass(CvAnalysis.class);
        verify(analysisRepository).save(captor.capture());
        CvAnalysis saved = captor.getValue();
        assertEquals(new BigDecimal("82"), saved.getQualityScore());
        assertNotNull(saved.getParsedData());
        assertEquals(82, saved.getParsedData().get("overallScore").asInt());
        assertEquals(85, saved.getParsedData().get("skillMatchScore").asInt());
        assertEquals(75, saved.getParsedData().get("experienceScore").asInt());
        assertEquals(90, saved.getParsedData().get("educationScore").asInt());
        assertEquals("manual", saved.getParsedData().get("dataSource").asText());

        // Kiểm tra backward compatibility: strengths và gaps được gộp từ các sections
        assertTrue(saved.getParsedData().has("strengths"));
        assertTrue(saved.getParsedData().has("gaps"));
        assertEquals("Thành thạo Java và Spring Boot", saved.getParsedData().get("strengths").get(0).asText());
    }

    @Test
    @DisplayName("1b. Nhánh 1: Có file PDF đủ chữ (>= 100 ký tự) -> Ưu tiên dùng text PDF, dataSource = pdf")
    void testScoreCv_Branch1_PdfHasEnoughText_UsesPdfContent() {
        // Arrange
        doNothing().when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);
        when(currentUserService.currentAccount()).thenReturn(Optional.of(sampleAccount));
        doNothing().when(aiRateLimiter).checkRateLimit("candidate@kiemjob.com");
        when(cvRepository.findById(CV_ID)).thenReturn(Optional.of(sampleCv));
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(CV_ID)).thenReturn(Optional.of(samplePdfFile));

        byte[] fakePdfBytes = "Fake PDF content".getBytes();
        ResponseBytes<GetObjectResponse> s3Bytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                fakePdfBytes
        );
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(s3Bytes);

        String longPdfContent = """
                Nguyen Van A - Senior Backend Java Developer
                5+ years experience building distributed microservices with Spring Boot, Docker, Kubernetes.
                Skills: Java 21, Spring Boot, PostgreSQL, Kafka, Redis, AWS S3.
                Education: Bachelor of Computer Science, Danang University of Technology.
                """;
        when(pdfTextExtractor.extractText(fakePdfBytes)).thenReturn(longPdfContent);

        when(promptTemplates.render(eq("cv-scoring-system"), any(java.util.Map.class))).thenReturn("System Prompt");
        when(promptTemplates.render(eq("cv-scoring-user"), any(java.util.Map.class))).thenReturn("User Prompt");
        when(groqClient.generateJson(anyString(), anyString())).thenReturn(VALID_AI_RESPONSE);

        when(analysisRepository.save(any(CvAnalysis.class))).thenAnswer(invocation -> {
            CvAnalysis entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 200L);
            return entity;
        });

        // Act
        CvAnalysisResponse response = cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, null);

        // Assert
        assertNotNull(response);
        assertEquals(200L, response.getId());
        assertEquals("pdf", response.getDataSource());
        assertEquals(new BigDecimal("82"), response.getQualityScore());

        // Đảm bảo không query database các phần con khi đã có PDF
        verify(cvCareerObjectiveRepository, never()).findByCvId(any());
        verify(cvSkillGroupRepository, never()).findAllByCvIdOrderByDisplayOrderAscIdAsc(any());
    }

    @Test
    @DisplayName("1c. Nhánh 2: PDF ảnh/quá ngắn (< 100 ký tự) hoặc lỗi S3 -> Fallback sang DB (manual)")
    void testScoreCv_Branch2_PdfImageOrS3Error_FallbacksToDbText() {
        // Arrange
        doNothing().when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);
        when(currentUserService.currentAccount()).thenReturn(Optional.of(sampleAccount));
        doNothing().when(aiRateLimiter).checkRateLimit("candidate@kiemjob.com");
        when(cvRepository.findById(CV_ID)).thenReturn(Optional.of(sampleCv));
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(CV_ID)).thenReturn(Optional.of(samplePdfFile));

        // Giả lập lỗi S3 khi đọc file PDF
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 connection error"));

        // Dữ liệu DB có sẵn
        when(cvCareerObjectiveRepository.findByCvId(CV_ID)).thenReturn(Optional.empty());
        when(cvSkillGroupRepository.findAllByCvIdOrderByDisplayOrderAscIdAsc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvWorkExperienceRepository.findByCv_IdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvEducationRepository.findByCv_IdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvProjectRepository.findAllByCvIdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvCertificateRepository.findAllByCvIdOrderByCertificateDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvAwardRepository.findAllByCvIdOrderByAwardDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvHobbyRepository.findAllByCvIdOrderByIdAsc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvMoreRepository.findByCv_IdOrderByIdAsc(CV_ID)).thenReturn(Collections.emptyList());
        when(candidateLinkRepository.findAllByCandidate_IdOrderByIdAsc(CANDIDATE_ID)).thenReturn(Collections.emptyList());

        when(promptTemplates.render(eq("cv-scoring-system"), any(java.util.Map.class))).thenReturn("System Prompt");
        when(promptTemplates.render(eq("cv-scoring-user"), any(java.util.Map.class))).thenReturn("User Prompt");
        when(groqClient.generateJson(anyString(), anyString())).thenReturn(VALID_AI_RESPONSE);

        when(analysisRepository.save(any(CvAnalysis.class))).thenAnswer(invocation -> {
            CvAnalysis entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 201L);
            return entity;
        });

        // Act
        CvAnalysisResponse response = cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, null);

        // Assert
        assertNotNull(response);
        assertEquals(201L, response.getId());
        assertEquals("manual", response.getDataSource());
        verify(analysisRepository).save(any());
    }

    @Test
    @DisplayName("1d. Nhánh 3: Cả hai nguồn đều trống (không có PDF, DB trống) -> Ném 422 UNPROCESSABLE_ENTITY")
    void testScoreCv_Branch3_BothPdfAndDbEmpty_Throws422() {
        // Arrange
        doNothing().when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);
        when(currentUserService.currentAccount()).thenReturn(Optional.of(sampleAccount));
        doNothing().when(aiRateLimiter).checkRateLimit("candidate@kiemjob.com");

        sampleCv.setTitle("");
        when(cvRepository.findById(CV_ID)).thenReturn(Optional.of(sampleCv));
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(CV_ID)).thenReturn(Optional.empty());

        when(cvCareerObjectiveRepository.findByCvId(CV_ID)).thenReturn(Optional.empty());
        when(cvSkillGroupRepository.findAllByCvIdOrderByDisplayOrderAscIdAsc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvWorkExperienceRepository.findByCv_IdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvEducationRepository.findByCv_IdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvProjectRepository.findAllByCvIdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvCertificateRepository.findAllByCvIdOrderByCertificateDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvAwardRepository.findAllByCvIdOrderByAwardDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvHobbyRepository.findAllByCvIdOrderByIdAsc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvMoreRepository.findByCv_IdOrderByIdAsc(CV_ID)).thenReturn(Collections.emptyList());
        when(candidateLinkRepository.findAllByCandidate_IdOrderByIdAsc(CANDIDATE_ID)).thenReturn(Collections.emptyList());

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, null)
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason() != null && ex.getReason().contains("Không đọc được nội dung CV"));
        verify(groqClient, never()).generateJson(anyString(), anyString());
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("2. AI trả về JSON sai cú pháp -> Ném lỗi UNPROCESSABLE_ENTITY và không lưu DB")
    void testScoreCv_MalformedJson_ThrowsUnprocessableEntity() {
        // Arrange
        doNothing().when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);
        when(currentUserService.currentAccount()).thenReturn(Optional.of(sampleAccount));
        doNothing().when(aiRateLimiter).checkRateLimit("candidate@kiemjob.com");
        when(cvRepository.findById(CV_ID)).thenReturn(Optional.of(sampleCv));
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(CV_ID)).thenReturn(Optional.empty());

        when(promptTemplates.render(anyString(), any(java.util.Map.class))).thenReturn("Prompt");
        when(groqClient.generateJson(anyString(), anyString())).thenReturn("This is not valid json content");

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, null)
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. AI trả về JSON thiếu các trường bắt buộc (overallScore hoặc sections) -> Ném lỗi UNPROCESSABLE_ENTITY")
    void testScoreCv_MissingRequiredFields_ThrowsUnprocessableEntity() {
        // Arrange
        doNothing().when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);
        when(currentUserService.currentAccount()).thenReturn(Optional.of(sampleAccount));
        doNothing().when(aiRateLimiter).checkRateLimit("candidate@kiemjob.com");
        when(cvRepository.findById(CV_ID)).thenReturn(Optional.of(sampleCv));
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(CV_ID)).thenReturn(Optional.empty());

        when(promptTemplates.render(anyString(), any(java.util.Map.class))).thenReturn("Prompt");
        // Missing overallScore and sections
        when(groqClient.generateJson(anyString(), anyString())).thenReturn("{\"summary\": \"Chỉ có summary\"}");

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, null)
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Vượt quá giới hạn tần suất (Rate limit) -> Ném lỗi TOO_MANY_REQUESTS và không gọi AI")
    void testScoreCv_RateLimitExceeded_ThrowsTooManyRequests() {
        // Arrange
        doNothing().when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);
        when(currentUserService.currentAccount()).thenReturn(Optional.of(sampleAccount));
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã vượt quá giới hạn gọi AI."))
                .when(aiRateLimiter).checkRateLimit("candidate@kiemjob.com");

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, null)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        verify(groqClient, never()).generateJson(anyString(), anyString());
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("5. Người dùng không có quyền thao tác trên CV -> Ném lỗi FORBIDDEN và không gọi AI")
    void testScoreCv_Unauthorized_ThrowsForbidden() {
        // Arrange
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác trên CV này"))
                .when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, null)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(groqClient, never()).generateJson(anyString(), anyString());
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("6. Lỗi kết nối AI (Timeout/Exception) -> Ném lỗi BAD_GATEWAY")
    void testScoreCv_GroqClientFailure_ThrowsBadGateway() {
        // Arrange
        doNothing().when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);
        when(currentUserService.currentAccount()).thenReturn(Optional.of(sampleAccount));
        doNothing().when(aiRateLimiter).checkRateLimit("candidate@kiemjob.com");
        when(cvRepository.findById(CV_ID)).thenReturn(Optional.of(sampleCv));
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(CV_ID)).thenReturn(Optional.empty());

        when(promptTemplates.render(anyString(), any(java.util.Map.class))).thenReturn("Prompt");
        when(groqClient.generateJson(anyString(), anyString())).thenThrow(new RuntimeException("Connection timed out"));

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, null)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. normalizeAnalysis: Điểm ngoài 0-100 bị clamp, category lạ bị đổi thành 'other'")
    void testNormalizeAnalysis_ScoreClamping_And_CategoryOther() throws Exception {
        String json = """
                {
                  "detectedField": "Kế toán trưởng",
                  "detectedLevel": "senior",
                  "overallScore": 150,
                  "sections": [
                    {
                      "category": "unknown_category_xyz",
                      "title": "Báo cáo tài chính",
                      "score": -20,
                      "found": ["Lập BCTC năm 2025"],
                      "comment": "Chưa rõ chi tiết"
                    }
                  ]
                }
                """;
        JsonNode root = objectMapper.readTree(json);
        ObjectNode normalized = cvAnalysisService.normalizeAnalysis(root, "manual");

        // overallScore clamp về 100
        assertEquals(100, normalized.get("overallScore").asInt());
        assertEquals("excellent", normalized.get("overallLevel").asText());

        JsonNode sec = normalized.get("sections").get(0);
        // Category lạ đổi thành other
        assertEquals("other", sec.get("category").asText());
        // Điểm âm clamp về 0
        assertEquals(0, sec.get("score").asInt());
        assertEquals("other-1", sec.get("key").asText());
    }

    @Test
    @DisplayName("8. normalizeAnalysis: Cắt tối đa 12 sections, bỏ section thiếu title/score, cắt bonuses <= 6, missingSections <= 5")
    void testNormalizeAnalysis_Limits_And_InvalidSectionsDropped() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"overallScore\": 75,\n");
        sb.append("  \"sections\": [\n");
        // Section không có title -> phải bị bỏ
        sb.append("    {\"category\": \"skills\", \"score\": 80},\n");
        // Section không có score -> phải bị bỏ
        sb.append("    {\"category\": \"skills\", \"title\": \"Không có điểm\"},\n");
        // 14 section hợp lệ -> chỉ lấy 12
        for (int i = 1; i <= 14; i++) {
            sb.append("    {\"category\": \"skills\", \"title\": \"Mục ").append(i).append("\", \"score\": 70}");
            if (i < 14) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        // 8 bonuses -> chỉ lấy 6, impact không hợp lệ đổi thành 'low'
        sb.append("  \"bonuses\": [\n");
        for (int i = 1; i <= 8; i++) {
            sb.append("    {\"title\": \"Bonus ").append(i).append("\", \"impact\": \"invalid_impact\"}");
            if (i < 8) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        // 7 missingSections -> chỉ lấy 5
        sb.append("  \"missingSections\": [\n");
        for (int i = 1; i <= 7; i++) {
            sb.append("    {\"title\": \"Missing ").append(i).append("\"}");
            if (i < 7) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");

        JsonNode root = objectMapper.readTree(sb.toString());
        ObjectNode normalized = cvAnalysisService.normalizeAnalysis(root, "pdf");

        // Đúng 12 sections
        assertEquals(12, normalized.get("sections").size());
        assertEquals("Mục 1", normalized.get("sections").get(0).get("title").asText());
        assertEquals("skills-1", normalized.get("sections").get(0).get("key").asText());

        // Bonuses <= 6 và impact chuyển thành low
        assertEquals(6, normalized.get("bonuses").size());
        assertEquals("low", normalized.get("bonuses").get(0).get("impact").asText());

        // missingSections <= 5
        assertEquals(5, normalized.get("missingSections").size());
    }

    @Test
    @DisplayName("9. normalizeAnalysis: Không có section nào hợp lệ -> Ném 422 UNPROCESSABLE_ENTITY")
    void testNormalizeAnalysis_NoValidSections_Throws422() throws Exception {
        String json = """
                {
                  "overallScore": 75,
                  "sections": [
                    {"category": "skills"},
                    {"title": "", "score": 80}
                  ]
                }
                """;
        JsonNode root = objectMapper.readTree(json);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                cvAnalysisService.normalizeAnalysis(root, "manual")
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason() != null && ex.getReason().contains("Không có mục nào"));
    }

    @Test
    @DisplayName("10. buildCvText mở rộng: Gom đủ Giải thưởng, Sở thích, Mục bổ sung, Liên kết ngoài và KHÔNG chứa email/SĐT")
    void testBuildCvText_ExpandedSections_IncludesAllAndMasksSensitiveData() {
        // Arrange
        doNothing().when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);
        when(currentUserService.currentAccount()).thenReturn(Optional.of(sampleAccount));
        doNothing().when(aiRateLimiter).checkRateLimit("candidate@kiemjob.com");
        when(cvRepository.findById(CV_ID)).thenReturn(Optional.of(sampleCv));
        when(cvPdfFileRepository.findByCvIdAndIsCurrentTrue(CV_ID)).thenReturn(Optional.empty());

        when(cvCareerObjectiveRepository.findByCvId(CV_ID)).thenReturn(Optional.empty());
        when(cvSkillGroupRepository.findAllByCvIdOrderByDisplayOrderAscIdAsc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvWorkExperienceRepository.findByCv_IdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvEducationRepository.findByCv_IdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvProjectRepository.findAllByCvIdOrderByStartDateDesc(CV_ID)).thenReturn(Collections.emptyList());
        when(cvCertificateRepository.findAllByCvIdOrderByCertificateDateDesc(CV_ID)).thenReturn(Collections.emptyList());

        // Dữ liệu mở rộng
        CvAward award = CvAward.builder()
                .awardName("Giải Nhất Hackathon 2024")
                .awardDate(LocalDate.of(2024, 5, 20))
                .description("Dự án AI trợ lý tuyển dụng")
                .build();
        when(cvAwardRepository.findAllByCvIdOrderByAwardDateDesc(CV_ID)).thenReturn(List.of(award));

        CvHobby hobby = CvHobby.builder()
                .hobbyName("Chạy marathon, Đọc sách công nghệ")
                .build();
        when(cvHobbyRepository.findAllByCvIdOrderByIdAsc(CV_ID)).thenReturn(List.of(hobby));

        CvMore more = CvMore.builder()
                .title("Ấn phẩm & Nghiên cứu")
                .content("Bài báo về Microservices trên Tạp chí Khoa học 2024")
                .build();
        when(cvMoreRepository.findByCv_IdOrderByIdAsc(CV_ID)).thenReturn(List.of(more));

        CandidateLink link = new CandidateLink();
        link.setType("GitHub");
        link.setLink("https://github.com/secret_candidate_profile");
        when(candidateLinkRepository.findAllByCandidate_IdOrderByIdAsc(CANDIDATE_ID)).thenReturn(List.of(link));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(promptTemplates.render(eq("cv-scoring-system"), any(java.util.Map.class))).thenReturn("System Prompt");
        when(promptTemplates.render(eq("cv-scoring-user"), any(java.util.Map.class))).thenAnswer(inv -> {
            java.util.Map<String, Object> map = inv.getArgument(1);
            return "<cv>" + map.get("cvText") + "</cv>";
        });

        when(groqClient.generateJson(anyString(), userPromptCaptor.capture())).thenReturn(VALID_AI_RESPONSE);

        when(analysisRepository.save(any(CvAnalysis.class))).thenAnswer(invocation -> {
            CvAnalysis entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 300L);
            return entity;
        });

        // Act
        CvAnalysisResponse response = cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, null);

        // Assert
        assertNotNull(response);
        String capturedPrompt = userPromptCaptor.getValue();
        assertNotNull(capturedPrompt);

        // Đảm bảo có các mục mới với định dạng === TÊN MỤC ===
        assertTrue(capturedPrompt.contains("=== GIẢI THƯỞNG ==="));
        assertTrue(capturedPrompt.contains("Giải Nhất Hackathon 2024"));
        assertTrue(capturedPrompt.contains("=== SỞ THÍCH ==="));
        assertTrue(capturedPrompt.contains("Chạy marathon"));
        assertTrue(capturedPrompt.contains("=== ẤN PHẨM & NGHIÊN CỨU ==="));
        assertTrue(capturedPrompt.contains("Bài báo về Microservices"));
        assertTrue(capturedPrompt.contains("=== LIÊN KẾT NGOÀI ==="));
        assertTrue(capturedPrompt.contains("GitHub: có"));

        // Bảo mật: Tuyệt đối KHÔNG chứa link đầy đủ, email hoặc số điện thoại
        assertFalse(capturedPrompt.contains("https://github.com/secret_candidate_profile"));
        assertFalse(capturedPrompt.contains("candidate@kiemjob.com"));
        assertFalse(capturedPrompt.contains("09"));
    }

    @Test
    @DisplayName("8. Chấm điểm lại CV đã có kết quả trước đó: Cập nhật bản ghi cũ (Upsert), không tạo trùng lặp cv_id")
    void testScoreCv_ExistingAnalysis_UpdatesRecord() {
        // Arrange
        doNothing().when(cvOwnershipService).checkCvWriteAccess(CV_ID, CANDIDATE_ID);
        when(currentUserService.currentAccount()).thenReturn(Optional.of(sampleAccount));
        doNothing().when(aiRateLimiter).checkRateLimit("candidate@kiemjob.com");
        when(cvRepository.findById(CV_ID)).thenReturn(Optional.of(sampleCv));

        CvAnalysis existingAnalysis = CvAnalysis.builder()
                .id(999L)
                .cv(sampleCv)
                .qualityScore(new BigDecimal("50"))
                .analysisStatus("failed")
                .build();
        when(analysisRepository.findByCv_Id(CV_ID)).thenReturn(Optional.of(existingAnalysis));

        when(promptTemplates.render(eq("cv-scoring-system"), any(java.util.Map.class))).thenReturn("System Prompt");
        when(promptTemplates.render(eq("cv-scoring-user"), any(java.util.Map.class))).thenReturn("User Prompt");
        when(groqClient.generateJson(anyString(), anyString())).thenReturn(VALID_AI_RESPONSE);
        when(analysisRepository.save(any(CvAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CvAnalysisResponse response = cvAnalysisService.scoreCv(CANDIDATE_ID, CV_ID, null);

        // Assert
        assertNotNull(response);
        assertEquals(999L, response.getId()); // Giữ nguyên ID cũ
        assertEquals("completed", response.getAnalysisStatus());
        assertEquals(new BigDecimal("82"), response.getQualityScore());
        assertNotNull(response.getSummary());
        assertNotNull(response.getAnalyzedAt());
        assertNotNull(response.getUpdatedAt());

        ArgumentCaptor<CvAnalysis> captor = ArgumentCaptor.forClass(CvAnalysis.class);
        verify(analysisRepository).save(captor.capture());
        CvAnalysis saved = captor.getValue();
        assertEquals(999L, saved.getId());
        assertEquals("completed", saved.getAnalysisStatus());
    }

    @Test
    @DisplayName("9. CRUD: getByCvId trả về kết quả phân tích theo CV ID")
    void testGetByCvId_Success() {
        when(cvRepository.findByIdAndCandidate_Id(CV_ID, CANDIDATE_ID)).thenReturn(Optional.of(sampleCv));

        CvAnalysis analysis = CvAnalysis.builder()
                .id(101L)
                .cv(sampleCv)
                .summary("Tổng quan CV xuất sắc")
                .qualityScore(new BigDecimal("95.00"))
                .analysisStatus("completed")
                .build();
        when(analysisRepository.findByCv_Id(CV_ID)).thenReturn(Optional.of(analysis));

        Optional<CvAnalysisResponse> result = cvAnalysisService.getByCvId(CANDIDATE_ID, CV_ID);

        assertTrue(result.isPresent());
        assertEquals(101L, result.get().getId());
        assertEquals("Tổng quan CV xuất sắc", result.get().getSummary());
        assertEquals("completed", result.get().getAnalysisStatus());
        assertEquals(new BigDecimal("95.00"), result.get().getQualityScore());
    }
}
