package com.kiemjob.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kiemjob.backend.ai.util.AiTextSanitizer;
import com.kiemjob.backend.ai.util.JsonResponseParser;
import com.kiemjob.backend.ai.util.PdfTextExtractor;
import com.kiemjob.backend.ai.util.PromptTemplates;
import com.kiemjob.backend.config.ai.GroqProperties;
import com.kiemjob.backend.dto.application.ApplicationAiScoreResponse;
import com.kiemjob.backend.dto.cv.CvAnalysisRequest;
import com.kiemjob.backend.dto.cv.CvAnalysisResponse;
import com.kiemjob.backend.dto.cv.CvScoreRequest;
import com.kiemjob.backend.entity.*;
import com.kiemjob.backend.repository.*;
import com.kiemjob.backend.security.AiRateLimiter;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.ai.GroqClient;
import com.kiemjob.backend.service.ai.cache.ApplicationAiScoreCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CvAnalysisService {

    public static final String PROMPT_VERSION = "v1.1";
    public static final double DEFAULT_WEIGHT_SKILL = 0.50;
    public static final double DEFAULT_WEIGHT_EXPERIENCE = 0.35;
    public static final double DEFAULT_WEIGHT_EDUCATION = 0.15;
    public static final int PENALTY_PER_MISSING_REQUIRED_SKILL = 10;
    public static final int PENALTY_PER_MISSING_PREFERRED_SKILL = 2;

    private static final int MAX_CV_TEXT_LENGTH = 10000;
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "objective", "skills", "experience", "projects", "education",
            "certificates", "languages", "awards", "activities",
            "publications", "references", "other"
    );
    private static final Set<String> VALID_SENIORITIES = Set.of(
            "junior", "mid", "senior", "lead", "intern", "unknown"
    );
    private static final Set<String> VALID_EDUCATION_LEVELS = Set.of(
            "dai_hoc", "cao_dang", "thac_si", "tien_si", "khac", "unknown"
    );

    private final CvAnalysisRepository analysisRepository;
    private final CvRepository cvRepository;
    private final CvCareerObjectiveRepository cvCareerObjectiveRepository;
    private final CvSkillGroupRepository cvSkillGroupRepository;
    private final CvWorkExperienceRepository cvWorkExperienceRepository;
    private final CvEducationRepository cvEducationRepository;
    private final CvProjectRepository cvProjectRepository;
    private final CvCertificateRepository cvCertificateRepository;
    private final CvAwardRepository cvAwardRepository;
    private final CvHobbyRepository cvHobbyRepository;
    private final CvMoreRepository cvMoreRepository;
    private final CandidateLinkRepository candidateLinkRepository;
    private final CvPdfFileRepository cvPdfFileRepository;
    private final JobPostRepository jobPostRepository;
    private final ApplicationRepository applicationRepository;
    private final JobDetailRepository jobDetailRepository;
    private final JobPostSpecializationRepository jobPostSpecializationRepository;
    private final JobSkillRepository jobSkillRepository;
    private final ApplicationAiScoreCache aiScoreCache;
    private final GroqProperties groqProperties;
    private final S3Client s3Client;
    private final PdfTextExtractor pdfTextExtractor;
    private final PromptTemplates promptTemplates;
    private final GroqClient groqClient;
    private final JsonResponseParser jsonResponseParser;
    private final AiRateLimiter aiRateLimiter;
    private final CurrentUserService currentUserService;
    private final CvOwnershipService cvOwnershipService;
    private final ObjectMapper objectMapper;

    /**
     * Chấm điểm và phân tích CV bằng AI (Groq API).
     */
    @Transactional
    public CvAnalysisResponse scoreCv(Long candidateId, Long cvId, CvScoreRequest request) {
        // 1. Kiểm tra quyền sở hữu CV
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);

        // 2. Kiểm tra giới hạn tần suất gọi AI (AiRateLimiter)
        String userIdentifier = currentUserService.currentAccount()
                .map(acc -> acc.getEmail() != null ? acc.getEmail() : String.valueOf(acc.getId()))
                .orElse("anonymous");
        aiRateLimiter.checkRateLimit(userIdentifier);

        // 3. Lấy thông tin CV
        Cv cv = cvRepository.findById(cvId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy CV"
                ));

        // 4. Lấy nội dung CV: Ưu tiên đọc file PDF hiện tại từ S3, fallback về DB (manual)
        String cvText = null;
        String dataSource = "manual";
        Optional<CvPdfFile> currentPdfOpt = cvPdfFileRepository.findByCvIdAndIsCurrentTrue(cvId);
        if (currentPdfOpt.isPresent()) {
            CvPdfFile pdfFile = currentPdfOpt.get();
            try {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(pdfFile.getBucketName())
                        .key(pdfFile.getObjectKey())
                        .build();
                byte[] pdfBytes = s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
                String extracted = pdfTextExtractor.extractText(pdfBytes);
                if (extracted != null && extracted.trim().length() >= 100) {
                    cvText = extracted.trim();
                    dataSource = "pdf";
                    log.info("Trích xuất nội dung từ PDF cho CV ID: {}, độ dài: {} ký tự", cvId, cvText.length());
                } else {
                    log.warn("Nội dung text từ PDF quá ngắn (< 100 ký tự) cho CV ID: {}, có thể là ảnh scan.", cvId);
                }
            } catch (Exception ex) {
                log.warn("Lỗi đọc PDF từ S3 cho CV ID: {}. Fallback về DB. Lỗi: {}", cvId, ex.getMessage());
            }
        }

        if (cvText == null) {
            String dbCvText = buildCvText(cvId, cv);
            if (dbCvText != null && !dbCvText.isBlank()) {
                cvText = dbCvText.trim();
                dataSource = "manual";
                log.info("Sử dụng nội dung CV từ DB cho CV ID: {}, độ dài: {} ký tự", cvId, cvText.length());
            }
        }

        if (cvText == null || cvText.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Không đọc được nội dung CV. Nếu file PDF là ảnh/scan, hãy dùng file có thể bôi đen chữ hoặc nhập thông tin CV thủ công."
            );
        }

        String truncatedCvText = truncateAtLineBoundary(cvText, MAX_CV_TEXT_LENGTH);

        // 5. Chuẩn bị thông tin vị trí công việc cho Prompt
        String jobTitle = "Vị trí chuyên môn theo định hướng CV";
        String jobLevel = "Tương ứng với kinh nghiệm";
        String jobDescription = "Đánh giá mức độ hoàn thiện, chuyên nghiệp và tính cạnh tranh của hồ sơ ứng viên trên thị trường lao động";
        String jobRequirements = "Kỹ năng chuyên môn vững vàng, kinh nghiệm thực tế rõ ràng, học vấn và chứng chỉ phù hợp";

        if (request != null) {
            if (request.getJobPostId() != null) {
                jobPostRepository.findById(request.getJobPostId()).ifPresent(jp -> {
                    if (jp.getTitle() != null && !jp.getTitle().isBlank()) {
                        // Cập nhật thông tin từ JobPost
                    }
                });
            }
            if (request.getTargetJobTitle() != null && !request.getTargetJobTitle().isBlank()) {
                jobTitle = request.getTargetJobTitle().trim();
            } else if (cv.getTitle() != null && !cv.getTitle().isBlank()) {
                jobTitle = cv.getTitle().trim();
            }
            if (request.getTargetJobLevel() != null && !request.getTargetJobLevel().isBlank()) {
                jobLevel = request.getTargetJobLevel().trim();
            }
            if (request.getTargetJobDescription() != null && !request.getTargetJobDescription().isBlank()) {
                jobDescription = request.getTargetJobDescription().trim();
            }
            if (request.getTargetJobRequirements() != null && !request.getTargetJobRequirements().isBlank()) {
                jobRequirements = request.getTargetJobRequirements().trim();
            }
        } else if (cv.getTitle() != null && !cv.getTitle().isBlank()) {
            jobTitle = cv.getTitle().trim();
        }

        // 6. Render prompts
        String systemPrompt = promptTemplates.render("cv-scoring-system", Map.of(
                "jobTitle", jobTitle,
                "jobLevel", jobLevel,
                "jobDescription", jobDescription,
                "jobRequirements", jobRequirements
        ));
        String userPrompt = promptTemplates.render("cv-scoring-user", Map.of(
                "cvText", truncatedCvText
        ));

        // 7. Gọi GroqClient
        log.info("Bắt đầu chấm điểm AI cho CV ID: {}", cvId);
        String rawJson;
        try {
            rawJson = groqClient.generateJson(systemPrompt, userPrompt);
        } catch (Exception ex) {
            log.error("Lỗi khi gọi Groq AI để chấm điểm CV ID {}: {}", cvId, ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Dịch vụ AI chấm điểm CV tạm thời không khả dụng. Vui lòng thử lại sau.",
                    ex
            );
        }

        // 8. Phân tích cú pháp và xác thực JSON phản hồi từ AI
        JsonNode rootNode;
        try {
            String cleanJson = jsonResponseParser.cleanJson(rawJson);
            rootNode = objectMapper.readTree(cleanJson);
        } catch (Exception ex) {
            log.error("AI trả về phản hồi không thể phân tích cú pháp JSON cho CV ID {}: {}", cvId, ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Phản hồi từ AI không đúng định dạng JSON.",
                    ex
            );
        }

        if (!rootNode.isObject() || !rootNode.has("overallScore") || !rootNode.get("overallScore").isNumber()
                || !rootNode.has("sections") || !rootNode.get("sections").isArray()) {
            log.error("AI trả về JSON thiếu các trường bắt buộc (overallScore hoặc sections) cho CV ID {}", cvId);
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Kết quả chấm điểm từ AI thiếu các trường dữ liệu cần thiết (overallScore hoặc sections)."
            );
        }

        // 9. Chuẩn hóa và làm sạch dữ liệu AI trả về
        ObjectNode normalizedNode = normalizeAnalysis(rootNode, dataSource);

        BigDecimal qualityScore = BigDecimal.valueOf(normalizedNode.get("overallScore").asInt());
        String suggestions = buildSuggestionsText(normalizedNode);
        String summary = normalizedNode.path("summary").asText("").trim();

        // 10. Lưu kết quả vào cơ sở dữ liệu (UPSERT theo cvId vì cv_id là UNIQUE)
        CvAnalysis analysis = analysisRepository.findByCv_Id(cvId)
                .orElseGet(() -> CvAnalysis.builder()
                        .cv(cv)
                        .build());

        analysis.setParsedData(normalizedNode);
        analysis.setSummary(summary.isEmpty() ? null : summary);
        analysis.setQualityScore(qualityScore);
        analysis.setSuggestions(suggestions);
        analysis.setAnalysisStatus("completed");
        analysis.setAnalyzedAt(LocalDateTime.now());
        analysis.setUpdatedAt(LocalDateTime.now());

        CvAnalysis saved = analysisRepository.save(analysis);
        log.info("Chấm điểm AI thành công cho CV ID: {}, QualityScore: {}, Nguồn dữ liệu: {}", cvId, qualityScore, dataSource);
        return CvAnalysisResponse.fromEntity(saved);
    }

    /**
     * Trích xuất văn bản từ CV: Ưu tiên đọc từ file PDF trên S3, nếu không có hoặc là ảnh scan thì fallback về DB.
     */
    public String extractCvText(Long cvId, Cv cv) {
        String cvText = null;
        Optional<CvPdfFile> currentPdfOpt = cvPdfFileRepository.findByCvIdAndIsCurrentTrue(cvId);
        if (currentPdfOpt.isPresent()) {
            CvPdfFile pdfFile = currentPdfOpt.get();
            try {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(pdfFile.getBucketName())
                        .key(pdfFile.getObjectKey())
                        .build();
                byte[] pdfBytes = s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
                String extracted = pdfTextExtractor.extractText(pdfBytes);
                if (extracted != null && extracted.trim().length() >= 100) {
                    cvText = extracted.trim();
                    log.info("Trích xuất nội dung từ PDF cho CV ID: {}, độ dài: {} ký tự", cvId, cvText.length());
                } else {
                    log.warn("Nội dung text từ PDF quá ngắn (< 100 ký tự) cho CV ID: {}, có thể là ảnh scan.", cvId);
                }
            } catch (Exception ex) {
                log.warn("Lỗi đọc PDF từ S3 cho CV ID: {}. Fallback về DB. Lỗi: {}", cvId, ex.getMessage());
            }
        }

        if (cvText == null) {
            String dbCvText = buildCvText(cvId, cv);
            if (dbCvText != null && !dbCvText.isBlank()) {
                cvText = dbCvText.trim();
                log.info("Sử dụng nội dung CV từ DB cho CV ID: {}, độ dài: {} ký tự", cvId, cvText.length());
            }
        }
        return cvText;
    }

    /**
     * Chế độ chấm điểm AI chuyên sâu: So sánh CV với yêu cầu cụ thể của bài đăng tuyển dụng (JobPost).
     * Kết quả được trả về trực tiếp (on-demand), cache trong bộ nhớ (ApplicationAiScoreCache),
     * KHÔNG lưu vào cơ sở dữ liệu.
     */
    public ApplicationAiScoreResponse scoreApplicationAgainstJob(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ ứng tuyển #" + applicationId));

        JobPost jobPost = application.getJobPost();
        Cv cv = application.getCv();
        if (jobPost == null || cv == null) {
            log.error("Đơn ứng tuyển #{} thiếu thông tin JobPost hoặc CV", applicationId);
            return ApplicationAiScoreResponse.builder()
                    .applicationId(applicationId)
                    .status("FAILED")
                    .summary("Thiếu thông tin bài tuyển dụng hoặc CV để chấm điểm")
                    .scoredAt(LocalDateTime.now())
                    .cached(false)
                    .build();
        }

        // 1. Kiểm tra quyền: Chỉ admin hoặc recruiter cùng công ty với tin tuyển dụng
        if (!currentUserService.isAdmin()) {
            Recruiter recruiter = currentUserService.requireRecruiter();
            if (jobPost.getCompany() == null || recruiter.getCompanyId() == null
                    || !jobPost.getCompany().getId().equals(recruiter.getCompanyId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Bạn không có quyền chấm điểm đơn ứng tuyển này");
            }
        }

        // 2. Kiểm tra giới hạn tần suất gọi AI (AiRateLimiter)
        String userIdentifier = currentUserService.currentAccount()
                .map(acc -> acc.getEmail() != null ? acc.getEmail() : String.valueOf(acc.getId()))
                .orElse("anonymous");
        aiRateLimiter.checkRateLimit(userIdentifier);

        // 3. Trích xuất text từ CV
        String cvText = extractCvText(cv.getId(), cv);
        if (cvText == null || cvText.trim().length() < 100) {
            log.warn("CV ID #{} không đọc được văn bản (text rỗng hoặc < 100 ký tự)", cv.getId());
            return ApplicationAiScoreResponse.builder()
                    .applicationId(applicationId)
                    .status("NO_TEXT")
                    .summary("Không trích xuất được văn bản từ CV. Nếu file PDF là ảnh scan, vui lòng sử dụng file có văn bản có thể chọn hoặc nhập thủ công.")
                    .scoredAt(LocalDateTime.now())
                    .cached(false)
                    .build();
        }

        // Che thông tin cá nhân và giới hạn ký tự (giữ nguyên ngắt dòng)
        String maskedCvText = AiTextSanitizer.maskPii(truncateAtLineBoundary(cvText, MAX_CV_TEXT_LENGTH));

        // 4. Chuẩn bị đối tượng JobPost có cấu trúc
        String jobTitle = (jobPost.getTitle() != null && !jobPost.getTitle().isBlank())
                ? jobPost.getTitle().trim() : "Vị trí tuyển dụng";
        String jobEducation = (jobPost.getEducationalRequirement() != null && !jobPost.getEducationalRequirement().isBlank())
                ? jobPost.getEducationalRequirement().trim() : "Không yêu cầu cụ thể";
        String jobWorkingMode = jobPost.getWorkingMode() != null ? jobPost.getWorkingMode() : "Toàn thời gian";

        // Gom kỹ năng & chuyên môn yêu cầu
        List<String> requiredSkillNames = new ArrayList<>();
        if (jobSkillRepository != null) {
            List<JobSkill> jobSkills = jobSkillRepository.findByJobPost_IdOrderByIdAsc(jobPost.getId());
            for (JobSkill js : jobSkills) {
                if (js.getSkill() != null && js.getSkill().getName() != null) {
                    requiredSkillNames.add(js.getSkill().getName());
                }
            }
        }
        List<JobPostSpecialization> specializations = jobPostSpecializationRepository
                .findByJobPost_IdOrderBySpecialization_NameAsc(jobPost.getId());
        for (JobPostSpecialization s : specializations) {
            if (s.getSpecialization() != null && s.getSpecialization().getName() != null) {
                requiredSkillNames.add(s.getSpecialization().getName());
            }
        }
        String jobSpecializations = requiredSkillNames.isEmpty() ? "Không có yêu cầu kỹ năng cố định" : String.join(", ", requiredSkillNames);

        List<JobDetail> details = jobDetailRepository.findByJobPost_IdOrderByIdAsc(jobPost.getId());
        StringBuilder descBuilder = new StringBuilder();
        StringBuilder reqBuilder = new StringBuilder();

        for (JobDetail detail : details) {
            String title = detail.getTitle() != null ? detail.getTitle().toLowerCase() : "";
            String cleanContent = AiTextSanitizer.stripHtmlPreservingBreaks(detail.getContent());
            if (cleanContent.isBlank()) continue;

            if (title.contains("yêu cầu") || title.contains("requirement") || title.contains("kỹ năng")) {
                reqBuilder.append("• ").append(detail.getTitle()).append(":\n").append(cleanContent).append("\n\n");
            } else {
                descBuilder.append("• ").append(detail.getTitle()).append(":\n").append(cleanContent).append("\n\n");
            }
        }

        String jobDescription = descBuilder.length() > 0 ? descBuilder.toString().trim() : "Xem chi tiết tin tuyển dụng";
        String jobRequirements = reqBuilder.length() > 0 ? reqBuilder.toString().trim() : "Đáp ứng năng lực theo vị trí";

        // 5. Kiểm tra cache trong bộ nhớ theo applicationId + inputHash
        String inputForHash = maskedCvText + "|" + jobPost.getId() + "|" + jobTitle + "|" + jobEducation + "|" + jobRequirements + "|" + PROMPT_VERSION;
        String inputHash = AiTextSanitizer.sha256Hex(inputForHash);
        String cacheKey = applicationId + ":" + inputHash;

        Optional<ApplicationAiScoreResponse> cachedOpt = aiScoreCache.get(cacheKey);
        if (cachedOpt.isPresent()) {
            log.info("Sử dụng kết quả chấm điểm AI từ cache cho đơn #{} (hash: {})", applicationId, inputHash);
            return cachedOpt.get();
        }

        // 6. Render prompt và gọi GroqClient (tối đa 1 retry khi gặp lỗi tạm thời)
        String systemPrompt = promptTemplates.render("cv-vs-job-system", Map.of());
        String userPrompt = promptTemplates.render("cv-vs-job-user", Map.of(
                "jobTitle", jobTitle,
                "jobEducation", jobEducation,
                "jobWorkingMode", jobWorkingMode,
                "jobSpecializations", jobSpecializations,
                "jobDescription", jobDescription,
                "jobRequirements", jobRequirements,
                "cvText", maskedCvText
        ));

        String rawJson = null;
        Exception lastEx = null;
        for (int attempt = 0; attempt <= 1; attempt++) {
            try {
                rawJson = groqClient.generateJson(systemPrompt, userPrompt);
                if (rawJson != null && !rawJson.isBlank()) {
                    break;
                }
            } catch (Exception ex) {
                lastEx = ex;
                log.warn("Lần gọi AI thứ {} thất bại cho đơn #{}: {}", attempt + 1, applicationId, ex.getMessage());
                if (attempt == 0) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        if (rawJson == null || rawJson.isBlank()) {
            log.error("Lỗi khi gọi Groq AI để chấm điểm CV vs Job cho đơn #{}: {}", applicationId, lastEx != null ? lastEx.getMessage() : "unknown");
            return ApplicationAiScoreResponse.builder()
                    .applicationId(applicationId)
                    .status("FAILED")
                    .summary("Lỗi kết nối dịch vụ AI hoặc dịch vụ đang quá tải. Vui lòng thử lại.")
                    .scoredAt(LocalDateTime.now())
                    .cached(false)
                    .build();
        }

        // 7. Phân tích JSON phản hồi
        JsonNode rootNode;
        try {
            String cleanJson = jsonResponseParser.cleanJson(rawJson);
            rootNode = objectMapper.readTree(cleanJson);
        } catch (Exception ex) {
            log.error("AI trả về JSON không thể phân tích cú pháp cho đơn #{}: {}", applicationId, ex.getMessage());
            return ApplicationAiScoreResponse.builder()
                    .applicationId(applicationId)
                    .status("FAILED")
                    .summary("Phản hồi từ AI không đúng định dạng JSON chuẩn. Vui lòng thử lại.")
                    .scoredAt(LocalDateTime.now())
                    .cached(false)
                    .build();
        }

        int skillScore = JsonResponseParser.clamp(rootNode.path("skillMatchScore").asInt(0));
        int experienceScore = JsonResponseParser.clamp(rootNode.path("experienceScore").asInt(0));
        Integer educationScore = null;
        if (rootNode.hasNonNull("educationScore")) {
            educationScore = JsonResponseParser.clamp(rootNode.path("educationScore").asInt(0));
        }

        // 8. Tính điểm tổng bằng CODE (Deterministic Weighting)
        // Mặc định: skill 50%, experience 35%, education 15%
        // Nếu không yêu cầu học vấn: chia lại 50/35 thành ~58.8% và ~41.2%
        boolean hasEducationRequirement = jobPost.getEducationalRequirement() != null
                && !jobPost.getEducationalRequirement().isBlank()
                && !jobPost.getEducationalRequirement().toLowerCase().contains("không");

        double overallScoreDouble;
        if (hasEducationRequirement && educationScore != null) {
            overallScoreDouble = (skillScore * DEFAULT_WEIGHT_SKILL)
                    + (experienceScore * DEFAULT_WEIGHT_EXPERIENCE)
                    + (educationScore * DEFAULT_WEIGHT_EDUCATION);
        } else {
            double sumWeights = DEFAULT_WEIGHT_SKILL + DEFAULT_WEIGHT_EXPERIENCE;
            double wSkill = DEFAULT_WEIGHT_SKILL / sumWeights;
            double wExp = DEFAULT_WEIGHT_EXPERIENCE / sumWeights;
            overallScoreDouble = (skillScore * wSkill) + (experienceScore * wExp);
        }

        // Trừ điểm kỹ năng thiếu: thiếu bắt buộc (-10), thiếu ưu tiên (-2)
        int missingRequiredCount = 0;
        int missingPreferredCount = 0;
        List<Map<String, Object>> missingSkillsList = new ArrayList<>();
        JsonNode missingSkillsNode = rootNode.path("missingSkills");
        if (missingSkillsNode.isArray()) {
            for (JsonNode item : missingSkillsNode) {
                String skillName = item.path("skill").asText("");
                String type = item.path("type").asText("required").toLowerCase();
                if ("preferred".equals(type) || "nice-to-have".equals(type)) {
                    missingPreferredCount++;
                    type = "preferred";
                } else {
                    missingRequiredCount++;
                    type = "required";
                }
                missingSkillsList.add(Map.of("skill", skillName, "type", type));
            }
        }

        overallScoreDouble -= (missingRequiredCount * PENALTY_PER_MISSING_REQUIRED_SKILL);
        overallScoreDouble -= (missingPreferredCount * PENALTY_PER_MISSING_PREFERRED_SKILL);

        int finalOverallScore = Math.max(0, Math.min(100, (int) Math.round(overallScoreDouble)));

        // Trích xuất matchedSkills
        List<String> matchedSkillsList = new ArrayList<>();
        JsonNode matchedNode = rootNode.path("matchedSkills");
        if (matchedNode.isArray()) {
            for (JsonNode item : matchedNode) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    matchedSkillsList.add(item.asText());
                }
            }
        }

        // Trích xuất strengths (kèm bằng chứng trích dẫn)
        List<Map<String, Object>> strengthsList = new ArrayList<>();
        JsonNode strengthsNode = rootNode.path("strengths");
        if (strengthsNode.isArray()) {
            for (JsonNode item : strengthsNode) {
                String point = item.path("point").asText("");
                String evidence = item.path("evidenceQuote").asText("");
                if (!point.isBlank()) {
                    strengthsList.add(Map.of("point", point, "evidenceQuote", evidence));
                }
            }
        }

        // Trích xuất gaps
        List<String> gapsList = new ArrayList<>();
        JsonNode gapsNode = rootNode.path("gaps");
        if (gapsNode.isArray()) {
            for (JsonNode item : gapsNode) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    gapsList.add(item.asText());
                }
            }
        }

        String summary = rootNode.path("summary").asText("Đã hoàn tất đánh giá độ phù hợp.");

        ApplicationAiScoreResponse response = ApplicationAiScoreResponse.builder()
                .applicationId(applicationId)
                .status("SUCCESS")
                .overallScore(finalOverallScore)
                .skillScore(skillScore)
                .experienceScore(experienceScore)
                .educationScore(educationScore)
                .matchedSkills(matchedSkillsList)
                .missingSkills(missingSkillsList)
                .strengths(strengthsList)
                .gaps(gapsList)
                .summary(summary)
                .model(groqProperties.getModel())
                .promptVersion(PROMPT_VERSION)
                .scoredAt(LocalDateTime.now())
                .cached(false)
                .build();

        // 9. Lưu vào cache trong bộ nhớ server
        aiScoreCache.put(cacheKey, response);

        log.info("Chấm điểm AI thành công cho đơn #{}: Overall={}, Skill={}, Exp={}, Edu={}",
                applicationId, finalOverallScore, skillScore, experienceScore, educationScore);
        return response;
    }

    /**
     * Chuẩn hóa kết quả phân tích từ AI theo schema chặt chẽ.
     */
    public ObjectNode normalizeAnalysis(JsonNode rootNode) {
        return normalizeAnalysis(rootNode, null);
    }

    /**
     * Chuẩn hóa kết quả phân tích từ AI theo schema chặt chẽ kèm nguồn dữ liệu.
     */
    public ObjectNode normalizeAnalysis(JsonNode rootNode, String dataSource) {
        if (rootNode == null || !rootNode.isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Dữ liệu phân tích không hợp lệ."
            );
        }

        ObjectNode normalized = objectMapper.createObjectNode();

        // 1. Ngành nghề & cấp độ (tối đa 80 ký tự)
        String detectedField = truncate(rootNode.path("detectedField").asText(null), 80);
        normalized.put("detectedField", (detectedField != null && !detectedField.isBlank()) ? detectedField : "Tổng hợp");

        String detectedLevel = truncate(rootNode.path("detectedLevel").asText(null), 80);
        normalized.put("detectedLevel", (detectedLevel != null && !detectedLevel.isBlank()) ? detectedLevel : "fresher");

        // 2. overallScore & overallLevel
        int overallScore = JsonResponseParser.clamp(rootNode.path("overallScore").asInt());
        normalized.put("overallScore", overallScore);

        String overallLevel = rootNode.path("overallLevel").asText("").trim().toLowerCase();
        if (!Set.of("excellent", "good", "fair", "weak").contains(overallLevel)) {
            overallLevel = overallScore >= 90 ? "excellent" :
                           overallScore >= 75 ? "good" :
                           overallScore >= 60 ? "fair" : "weak";
        }
        normalized.put("overallLevel", overallLevel);

        // 3. summary
        String summary = rootNode.path("summary").asText("").trim();
        normalized.put("summary", summary);

        // 4. topPriorities (tối đa 3 chuỗi <= 300 ký tự)
        ArrayNode prioritiesNode = normalized.putArray("topPriorities");
        if (rootNode.path("topPriorities").isArray()) {
            int count = 0;
            for (JsonNode item : rootNode.path("topPriorities")) {
                if (count >= 3) break;
                if (item.isTextual() && !item.asText().isBlank()) {
                    String p = truncate(item.asText(), 300);
                    if (p != null && !p.isBlank()) {
                        prioritiesNode.add(p);
                        count++;
                    }
                }
            }
        }

        // 5. sections (tối đa 12 mục hợp lệ)
        ArrayNode sectionsNode = normalized.putArray("sections");
        List<ObjectNode> validSections = new ArrayList<>();
        int bestSkillScore = 0;
        int bestExpScore = 0;
        int bestEduScore = 0;
        List<String> aggregatedStrengths = new ArrayList<>();
        List<String> aggregatedGaps = new ArrayList<>();

        if (rootNode.path("sections").isArray()) {
            int sectionIndex = 1;
            for (JsonNode sec : rootNode.path("sections")) {
                if (!sec.isObject()) continue;
                if (validSections.size() >= 12) break;

                // title: bắt buộc, tối đa 80 ký tự
                String title = truncate(sec.path("title").asText(null), 80);
                if (title == null || title.isBlank()) continue;

                // score: bắt buộc là số hợp lệ
                if (!sec.has("score") || !sec.get("score").isNumber()) continue;
                int score = JsonResponseParser.clamp(sec.get("score").asInt());

                // category: nếu không thuộc danh sách thì chuyển về other
                String cat = sec.path("category").asText("other").trim().toLowerCase();
                if (!VALID_CATEGORIES.contains(cat)) {
                    cat = "other";
                }

                // Cập nhật điểm thành phần theo category cho tương thích ngược
                if ("skills".equals(cat)) {
                    bestSkillScore = Math.max(bestSkillScore, score);
                } else if ("experience".equals(cat)) {
                    bestExpScore = Math.max(bestExpScore, score);
                } else if ("education".equals(cat)) {
                    bestEduScore = Math.max(bestEduScore, score);
                }

                // level
                String level = sec.path("level").asText("").trim().toLowerCase();
                if (!Set.of("excellent", "good", "fair", "weak").contains(level)) {
                    level = score >= 90 ? "excellent" :
                            score >= 75 ? "good" :
                            score >= 60 ? "fair" : "weak";
                }

                ObjectNode secObj = objectMapper.createObjectNode();
                secObj.put("key", cat + "-" + sectionIndex);
                secObj.put("category", cat);
                secObj.put("title", title);
                secObj.put("score", score);
                secObj.put("level", level);

                // found (tối đa 6 ý, mỗi ý <= 200 ký tự)
                ArrayNode foundNode = secObj.putArray("found");
                if (sec.path("found").isArray()) {
                    int fCount = 0;
                    for (JsonNode fItem : sec.path("found")) {
                        if (fCount >= 6) break;
                        if (fItem.isTextual() && !fItem.asText().isBlank()) {
                            String fText = truncate(fItem.asText(), 200);
                            if (fText != null && !fText.isBlank()) {
                                foundNode.add(fText);
                                fCount++;
                            }
                        }
                    }
                }

                // comment (tối đa 1200 ký tự)
                String comment = truncate(sec.path("comment").asText(""), 1200);
                secObj.put("comment", comment != null ? comment : "");

                // strengths (tối đa 3 ý, mỗi ý <= 300 ký tự)
                ArrayNode strNode = secObj.putArray("strengths");
                if (sec.path("strengths").isArray()) {
                    int sCount = 0;
                    for (JsonNode sItem : sec.path("strengths")) {
                        if (sCount >= 3) break;
                        if (sItem.isTextual() && !sItem.asText().isBlank()) {
                            String sText = truncate(sItem.asText(), 300);
                            if (sText != null && !sText.isBlank()) {
                                strNode.add(sText);
                                if (aggregatedStrengths.size() < 6) {
                                    aggregatedStrengths.add(sText);
                                }
                                sCount++;
                            }
                        }
                    }
                }

                // improvements (tối đa 3 ý, mỗi ý <= 300 ký tự)
                ArrayNode impNode = secObj.putArray("improvements");
                if (sec.path("improvements").isArray()) {
                    int iCount = 0;
                    for (JsonNode iItem : sec.path("improvements")) {
                        if (iCount >= 3) break;
                        if (iItem.isTextual() && !iItem.asText().isBlank()) {
                            String iText = truncate(iItem.asText(), 300);
                            if (iText != null && !iText.isBlank()) {
                                impNode.add(iText);
                                if (aggregatedGaps.size() < 6) {
                                    aggregatedGaps.add(iText);
                                }
                                iCount++;
                            }
                        }
                    }
                }

                validSections.add(secObj);
                sectionIndex++;
            }
        }

        if (validSections.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Không có mục nào trong CV được phân tích hợp lệ."
            );
        }

        for (ObjectNode s : validSections) {
            sectionsNode.add(s);
        }

        // 6. bonuses (tối đa 6 mục)
        ArrayNode bonusesNode = normalized.putArray("bonuses");
        if (rootNode.path("bonuses").isArray()) {
            int bCount = 0;
            for (JsonNode bItem : rootNode.path("bonuses")) {
                if (bCount >= 6) break;
                if (!bItem.isObject()) continue;
                String bTitle = truncate(bItem.path("title").asText(null), 100);
                if (bTitle == null || bTitle.isBlank()) continue;

                String bDetail = truncate(bItem.path("detail").asText(""), 300);
                String bImpact = bItem.path("impact").asText("low").trim().toLowerCase();
                if (!Set.of("high", "medium", "low").contains(bImpact)) {
                    bImpact = "low";
                }

                ObjectNode bObj = objectMapper.createObjectNode();
                bObj.put("title", bTitle);
                bObj.put("detail", bDetail != null ? bDetail : "");
                bObj.put("impact", bImpact);
                bonusesNode.add(bObj);
                bCount++;
            }
        }

        // 7. missingSections (tối đa 5 mục)
        ArrayNode missingNode = normalized.putArray("missingSections");
        if (rootNode.path("missingSections").isArray()) {
            int mCount = 0;
            for (JsonNode mItem : rootNode.path("missingSections")) {
                if (mCount >= 5) break;
                if (!mItem.isObject()) continue;
                String mTitle = truncate(mItem.path("title").asText(null), 100);
                if (mTitle == null || mTitle.isBlank()) continue;

                String mReason = truncate(mItem.path("reason").asText(""), 300);
                String mSuggestion = truncate(mItem.path("suggestion").asText(""), 300);

                ObjectNode mObj = objectMapper.createObjectNode();
                mObj.put("title", mTitle);
                mObj.put("reason", mReason != null ? mReason : "");
                mObj.put("suggestion", mSuggestion != null ? mSuggestion : "");
                missingNode.add(mObj);
                mCount++;
            }
        }

        // 8. Tương thích ngược với các trường cũ
        normalized.put("skillMatchScore", bestSkillScore);
        normalized.put("experienceScore", bestExpScore);
        normalized.put("educationScore", bestEduScore);

        ArrayNode strengthsNode = normalized.putArray("strengths");
        for (String st : aggregatedStrengths) {
            strengthsNode.add(st);
        }

        ArrayNode gapsNode = normalized.putArray("gaps");
        for (String gap : aggregatedGaps) {
            gapsNode.add(gap);
        }

        if (dataSource != null) {
            normalized.put("dataSource", dataSource);
        } else if (rootNode.has("dataSource")) {
            normalized.put("dataSource", rootNode.path("dataSource").asText("manual"));
        } else {
            normalized.put("dataSource", "manual");
        }

        // 9. searchProfile: chuẩn hóa khối tìm kiếm ứng viên
        ObjectNode searchProfileNode = normalized.putObject("searchProfile");
        JsonNode rawSearchProfile = rootNode.path("searchProfile");
        if (rawSearchProfile.isObject()) {
            // industry
            String industry = truncate(rawSearchProfile.path("industry").asText(null), 100);
            if (industry != null && !industry.isBlank()) {
                searchProfileNode.put("industry", industry.trim());
            } else {
                searchProfileNode.putNull("industry");
            }

            // jobTitles (mảng tối đa 5 phần tử, mỗi phần tử tối đa 100 ký tự)
            ArrayNode jobTitlesArr = searchProfileNode.putArray("jobTitles");
            if (rawSearchProfile.path("jobTitles").isArray()) {
                int count = 0;
                for (JsonNode jt : rawSearchProfile.path("jobTitles")) {
                    if (count >= 5) break;
                    if (jt.isTextual() && !jt.asText().isBlank()) {
                        String title = truncate(jt.asText().trim(), 100);
                        if (title != null && !title.isBlank()) {
                            jobTitlesArr.add(title);
                            count++;
                        }
                    }
                }
            }

            // seniority: junior|mid|senior|lead|intern|unknown
            String seniority = rawSearchProfile.path("seniority").asText("unknown").trim().toLowerCase();
            if (!VALID_SENIORITIES.contains(seniority)) {
                seniority = "unknown";
            }
            searchProfileNode.put("seniority", seniority);

            // yearsExperience: số thực >= 0, làm tròn 1 chữ số thập phân, clamp 0.0 - 50.0 hoặc null
            if (rawSearchProfile.has("yearsExperience") && rawSearchProfile.get("yearsExperience").isNumber()) {
                double rawYears = rawSearchProfile.get("yearsExperience").asDouble();
                if (rawYears >= 0 && rawYears <= 50) {
                    searchProfileNode.put("yearsExperience", Math.round(rawYears * 10.0) / 10.0);
                } else if (rawYears > 50) {
                    searchProfileNode.put("yearsExperience", 50.0);
                } else {
                    searchProfileNode.putNull("yearsExperience");
                }
            } else {
                searchProfileNode.putNull("yearsExperience");
            }

            // skills: viết thường, không dấu thừa, loại trùng, tối đa 30 mục
            ArrayNode skillsArr = searchProfileNode.putArray("skills");
            if (rawSearchProfile.path("skills").isArray()) {
                Set<String> uniqueSkills = new LinkedHashSet<>();
                for (JsonNode sk : rawSearchProfile.path("skills")) {
                    if (uniqueSkills.size() >= 30) break;
                    if (sk.isTextual() && !sk.asText().isBlank()) {
                        String cleanSkill = sk.asText().trim().toLowerCase().replaceAll("^[.,;:_\\-\\s]+|[.,;:_\\-\\s]+$", "");
                        if (!cleanSkill.isBlank()) {
                            cleanSkill = truncate(cleanSkill, 50);
                            if (cleanSkill != null && !cleanSkill.isBlank()) {
                                uniqueSkills.add(cleanSkill);
                            }
                        }
                    }
                }
                for (String s : uniqueSkills) {
                    skillsArr.add(s);
                }
            }

            // educationLevel: dai_hoc|cao_dang|thac_si|tien_si|khac|unknown
            String eduLevel = rawSearchProfile.path("educationLevel").asText("unknown").trim().toLowerCase();
            if (!VALID_EDUCATION_LEVELS.contains(eduLevel)) {
                eduLevel = "unknown";
            }
            searchProfileNode.put("educationLevel", eduLevel);

            // certifications (mảng tối đa 10 phần tử, mỗi phần tử tối đa 100 ký tự)
            ArrayNode certsArr = searchProfileNode.putArray("certifications");
            if (rawSearchProfile.path("certifications").isArray()) {
                int count = 0;
                for (JsonNode cert : rawSearchProfile.path("certifications")) {
                    if (count >= 10) break;
                    if (cert.isTextual() && !cert.asText().isBlank()) {
                        String c = truncate(cert.asText().trim(), 100);
                        if (c != null && !c.isBlank()) {
                            certsArr.add(c);
                            count++;
                        }
                    }
                }
            }

            // languages (mảng tối đa 10 phần tử, mỗi phần tử tối đa 50 ký tự)
            ArrayNode langsArr = searchProfileNode.putArray("languages");
            if (rawSearchProfile.path("languages").isArray()) {
                int count = 0;
                for (JsonNode lang : rawSearchProfile.path("languages")) {
                    if (count >= 10) break;
                    if (lang.isTextual() && !lang.asText().isBlank()) {
                        String l = truncate(lang.asText().trim(), 50);
                        if (l != null && !l.isBlank()) {
                            langsArr.add(l);
                            count++;
                        }
                    }
                }
            }

            // location
            String location = truncate(rawSearchProfile.path("location").asText(null), 100);
            if (location != null && !location.isBlank()) {
                searchProfileNode.put("location", location.trim());
            } else {
                searchProfileNode.putNull("location");
            }
        } else {
            // Khối searchProfile rỗng mặc định nếu AI không trả về
            searchProfileNode.putNull("industry");
            searchProfileNode.putArray("jobTitles");
            searchProfileNode.put("seniority", "unknown");
            searchProfileNode.putNull("yearsExperience");
            searchProfileNode.putArray("skills");
            searchProfileNode.put("educationLevel", "unknown");
            searchProfileNode.putArray("certifications");
            searchProfileNode.putArray("languages");
            searchProfileNode.putNull("location");
        }

        return normalized;
    }

    /**
     * Tạo chuỗi văn bản cho cột suggestions trong cơ sở dữ liệu.
     */
    public String buildSuggestionsText(ObjectNode node) {
        StringBuilder sb = new StringBuilder();

        String field = node.path("detectedField").asText("");
        String level = node.path("detectedLevel").asText("");
        if (!field.isBlank() || !level.isBlank()) {
            sb.append("Ngành nghề: ").append(field)
              .append(" | Cấp độ: ").append(level).append("\n\n");
        }

        String summary = node.path("summary").asText("");
        if (!summary.isBlank()) {
            sb.append("Tổng quan: ").append(summary).append("\n\n");
        }

        if (node.path("topPriorities").isArray() && !node.path("topPriorities").isEmpty()) {
            sb.append("Nên sửa trước:\n");
            int idx = 1;
            for (JsonNode p : node.path("topPriorities")) {
                sb.append(idx++).append(". ").append(p.asText()).append("\n");
            }
            sb.append("\n");
        }

        if (node.path("sections").isArray() && !node.path("sections").isEmpty()) {
            sb.append("Đánh giá chi tiết từng phần:\n");
            for (JsonNode sec : node.path("sections")) {
                String title = sec.path("title").asText("");
                int score = sec.path("score").asInt();
                String sLevel = sec.path("level").asText("");
                String comment = sec.path("comment").asText("");

                sb.append("• ").append(title).append(" [").append(score).append("/100 - ").append(sLevel).append("]:\n");
                if (!comment.isBlank()) {
                    sb.append("   - Nhận xét: ").append(comment).append("\n");
                }
                if (sec.path("strengths").isArray() && !sec.path("strengths").isEmpty()) {
                    sb.append("   - Điểm mạnh: ");
                    List<String> slist = new ArrayList<>();
                    sec.path("strengths").forEach(s -> slist.add(s.asText()));
                    sb.append(String.join("; ", slist)).append("\n");
                }
                if (sec.path("improvements").isArray() && !sec.path("improvements").isEmpty()) {
                    sb.append("   - Cần cải thiện: ");
                    List<String> ilist = new ArrayList<>();
                    sec.path("improvements").forEach(i -> ilist.add(i.asText()));
                    sb.append(String.join("; ", ilist)).append("\n");
                }
            }
            sb.append("\n");
        }

        if (node.path("bonuses").isArray() && !node.path("bonuses").isEmpty()) {
            sb.append("Điểm cộng:\n");
            for (JsonNode b : node.path("bonuses")) {
                sb.append("• ").append(b.path("title").asText(""));
                String impact = b.path("impact").asText("");
                if (!impact.isBlank()) {
                    sb.append(" (Tác động: ").append(impact).append(")");
                }
                String detail = b.path("detail").asText("");
                if (!detail.isBlank()) {
                    sb.append(": ").append(detail);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (node.path("missingSections").isArray() && !node.path("missingSections").isEmpty()) {
            sb.append("Mục nên bổ sung:\n");
            for (JsonNode m : node.path("missingSections")) {
                sb.append("• ").append(m.path("title").asText(""));
                String reason = m.path("reason").asText("");
                if (!reason.isBlank()) {
                    sb.append(" - Lý do: ").append(reason);
                }
                String suggestion = m.path("suggestion").asText("");
                if (!suggestion.isBlank()) {
                    sb.append(" -> Gợi ý: ").append(suggestion);
                }
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.length() <= maxLen) return trimmed;
        return trimmed.substring(0, maxLen).trim();
    }

    /**
     * Cắt bớt văn bản nếu vượt quá độ dài tối đa, ưu tiên cắt tại ranh giới dòng, không cắt giữa từ.
     */
    public String truncateAtLineBoundary(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        String sub = text.substring(0, maxLength);
        int lastNewline = sub.lastIndexOf('\n');
        if (lastNewline > maxLength / 2) {
            return sub.substring(0, lastNewline).trim();
        }
        int lastSpace = sub.lastIndexOf(' ');
        if (lastSpace > maxLength / 2) {
            return sub.substring(0, lastSpace).trim();
        }
        return sub.trim();
    }

    /**
     * Gom nội dung CV thành văn bản thuần có cấu trúc để gửi cho AI model.
     * Lưu ý bảo mật: Không đưa số điện thoại, email, địa chỉ chi tiết của ứng viên vào.
     */
    private String buildCvText(Long cvId, Cv cv) {
        StringBuilder sb = new StringBuilder();
        if (cv.getTitle() != null && !cv.getTitle().isBlank()) {
            sb.append("Tiêu đề CV: ").append(cv.getTitle().trim()).append("\n\n");
        }

        // 1. Mục tiêu nghề nghiệp
        cvCareerObjectiveRepository.findByCvId(cvId).ifPresent(obj -> {
            if (obj.getContent() != null && !obj.getContent().isBlank()) {
                sb.append("=== MỤC TIÊU NGHỀ NGHIỆP ===\n")
                  .append(obj.getContent().trim()).append("\n\n");
            }
        });

        // 2. Kỹ năng
        List<CvSkillGroup> skills = cvSkillGroupRepository.findAllByCvIdOrderByDisplayOrderAscIdAsc(cvId);
        if (!skills.isEmpty()) {
            sb.append("=== KỸ NĂNG ===\n");
            for (CvSkillGroup sg : skills) {
                sb.append("- ").append(sg.getTitle()).append(": ").append(sg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        // 3. Kinh nghiệm làm việc
        List<CvWorkExperience> experiences = cvWorkExperienceRepository.findByCv_IdOrderByStartDateDesc(cvId);
        if (!experiences.isEmpty()) {
            sb.append("=== KINH NGHIỆM LÀM VIỆC ===\n");
            for (CvWorkExperience exp : experiences) {
                sb.append("- Công ty: ").append(exp.getCompanyName())
                  .append(" | Vị trí: ").append(exp.getPosition())
                  .append(" (").append(exp.getStartDate()).append(" đến ")
                  .append(Boolean.TRUE.equals(exp.getIsCurrent()) ? "Hiện tại" : exp.getEndDate()).append(")\n");
                if (exp.getDescription() != null && !exp.getDescription().isBlank()) {
                    sb.append("  Mô tả: ").append(exp.getDescription().trim()).append("\n");
                }
            }
            sb.append("\n");
        }

        // 4. Học vấn
        List<CvEducation> educations = cvEducationRepository.findByCv_IdOrderByStartDateDesc(cvId);
        if (!educations.isEmpty()) {
            sb.append("=== HỌC VẤN ===\n");
            for (CvEducation edu : educations) {
                sb.append("- Trường: ").append(edu.getSchoolName())
                  .append(" (").append(edu.getStartDate()).append(" đến ").append(edu.getEndDate()).append(")\n");
                if (edu.getRank() != null && !edu.getRank().isBlank()) {
                    sb.append("  Xếp loại: ").append(edu.getRank()).append("\n");
                }
                if (edu.getAchivements() != null && !edu.getAchivements().isBlank()) {
                    sb.append("  Thành tựu: ").append(edu.getAchivements().trim()).append("\n");
                }
            }
            sb.append("\n");
        }

        // 5. Dự án tiêu biểu
        List<CvProject> projects = cvProjectRepository.findAllByCvIdOrderByStartDateDesc(cvId);
        if (!projects.isEmpty()) {
            sb.append("=== DỰ ÁN TIÊU BIỂU ===\n");
            for (CvProject proj : projects) {
                sb.append("- Dự án: ").append(proj.getProjectName())
                  .append(" | Vai trò: ").append(proj.getRole()).append("\n");
                if (proj.getTechnologies() != null && !proj.getTechnologies().isBlank()) {
                    sb.append("  Công nghệ: ").append(proj.getTechnologies()).append("\n");
                }
                if (proj.getDescription() != null && !proj.getDescription().isBlank()) {
                    sb.append("  Mô tả: ").append(proj.getDescription().trim()).append("\n");
                }
            }
            sb.append("\n");
        }

        // 6. Chứng chỉ
        List<CvCertificate> certificates = cvCertificateRepository.findAllByCvIdOrderByCertificateDateDesc(cvId);
        if (!certificates.isEmpty()) {
            sb.append("=== CHỨNG CHỈ ===\n");
            for (CvCertificate cert : certificates) {
                sb.append("- ").append(cert.getCertificateName())
                  .append(" (").append(cert.getCertificateDate()).append(")\n");
            }
            sb.append("\n");
        }

        // 7. Giải thưởng
        List<CvAward> awards = cvAwardRepository.findAllByCvIdOrderByAwardDateDesc(cvId);
        if (!awards.isEmpty()) {
            sb.append("=== GIẢI THƯỞNG ===\n");
            for (CvAward award : awards) {
                sb.append("- ").append(award.getAwardName());
                if (award.getAwardDate() != null) {
                    sb.append(" (").append(award.getAwardDate()).append(")");
                }
                if (award.getDescription() != null && !award.getDescription().isBlank()) {
                    sb.append(": ").append(award.getDescription().trim());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 8. Sở thích
        List<CvHobby> hobbies = cvHobbyRepository.findAllByCvIdOrderByIdAsc(cvId);
        if (!hobbies.isEmpty()) {
            List<String> names = hobbies.stream()
                    .map(CvHobby::getHobbyName)
                    .filter(h -> h != null && !h.isBlank())
                    .toList();
            if (!names.isEmpty()) {
                sb.append("=== SỞ THÍCH ===\n");
                sb.append(String.join(", ", names)).append("\n\n");
            }
        }

        // 9. Các mục tự thêm bổ sung
        List<CvMore> mores = cvMoreRepository.findByCv_IdOrderByIdAsc(cvId);
        if (!mores.isEmpty()) {
            for (CvMore more : mores) {
                String secTitle = (more.getTitle() != null && !more.getTitle().isBlank())
                        ? more.getTitle().trim().toUpperCase()
                        : "MỤC BỔ SUNG";
                sb.append("=== ").append(secTitle).append(" ===\n");
                if (more.getContent() != null && !more.getContent().isBlank()) {
                    sb.append(more.getContent().trim()).append("\n");
                }
                sb.append("\n");
            }
        }

        // 10. Liên kết ngoài (CandidateLink - chỉ ghi loại link, KHÔNG ghi URL để bảo mật quyền riêng tư)
        if (cv.getCandidate() != null && cv.getCandidate().getId() != null) {
            List<CandidateLink> links = candidateLinkRepository.findAllByCandidate_IdOrderByIdAsc(cv.getCandidate().getId());
            if (!links.isEmpty()) {
                sb.append("=== LIÊN KẾT NGOÀI ===\n");
                for (CandidateLink link : links) {
                    if (link.getType() != null && !link.getType().isBlank()) {
                        sb.append("- ").append(link.getType().trim()).append(": có\n");
                    }
                }
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    @Transactional
    public CvAnalysisResponse create(
            Long candidateId,
            Long cvId,
            CvAnalysisRequest request) {

        Cv cv = getOwnedCv(candidateId, cvId);

        // Do cv_id có ràng buộc UNIQUE, nếu đã tồn tại thì cập nhật lại bản ghi cũ thay vì tạo mới trùng lặp
        CvAnalysis analysis = analysisRepository.findByCv_Id(cvId)
                .orElseGet(() -> CvAnalysis.builder()
                        .cv(cv)
                        .build());

        analysis.setParsedData(request.getParsedData());
        analysis.setSummary(request.getSummary());
        analysis.setEmbedding(request.getEmbedding());
        analysis.setQualityScore(request.getQualityScore());
        analysis.setSuggestions(normalize(request.getSuggestions()));

        String status = request.getAnalysisStatus();
        if (status != null && !status.isBlank()) {
            analysis.setAnalysisStatus(status.trim().toLowerCase());
        } else if (analysis.getAnalysisStatus() == null) {
            analysis.setAnalysisStatus("pending");
        }

        if ("completed".equalsIgnoreCase(analysis.getAnalysisStatus())) {
            analysis.setAnalyzedAt(LocalDateTime.now());
        }
        analysis.setUpdatedAt(LocalDateTime.now());

        return CvAnalysisResponse.fromEntity(
                analysisRepository.save(analysis)
        );
    }

    @Transactional(readOnly = true)
    public List<CvAnalysisResponse> getAll(
            Long candidateId,
            Long cvId) {

        getOwnedCv(candidateId, cvId);

        return analysisRepository.findByCv_IdOrderByIdDesc(cvId)
                .stream()
                .map(CvAnalysisResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CvAnalysisResponse> getByCvId(
            Long candidateId,
            Long cvId) {

        getOwnedCv(candidateId, cvId);

        return analysisRepository.findByCv_Id(cvId)
                .map(CvAnalysisResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public CvAnalysisResponse getById(
            Long candidateId,
            Long cvId,
            Long analysisId) {

        getOwnedCv(candidateId, cvId);

        return CvAnalysisResponse.fromEntity(
                getAnalysis(analysisId, cvId)
        );
    }

    @Transactional
    public CvAnalysisResponse update(
            Long candidateId,
            Long cvId,
            Long analysisId,
            CvAnalysisRequest request) {

        getOwnedCv(candidateId, cvId);

        CvAnalysis analysis = getAnalysis(analysisId, cvId);

        analysis.setParsedData(request.getParsedData());
        if (request.getSummary() != null) {
            analysis.setSummary(request.getSummary());
        }
        if (request.getEmbedding() != null) {
            analysis.setEmbedding(request.getEmbedding());
        }
        analysis.setQualityScore(request.getQualityScore());
        analysis.setSuggestions(normalize(request.getSuggestions()));

        if (request.getAnalysisStatus() != null && !request.getAnalysisStatus().isBlank()) {
            analysis.setAnalysisStatus(request.getAnalysisStatus().trim().toLowerCase());
        }
        if ("completed".equalsIgnoreCase(analysis.getAnalysisStatus()) && analysis.getAnalyzedAt() == null) {
            analysis.setAnalyzedAt(LocalDateTime.now());
        }
        analysis.setUpdatedAt(LocalDateTime.now());

        return CvAnalysisResponse.fromEntity(
                analysisRepository.save(analysis)
        );
    }

    @Transactional
    public void delete(
            Long candidateId,
            Long cvId,
            Long analysisId) {

        getOwnedCv(candidateId, cvId);

        analysisRepository.delete(getAnalysis(analysisId, cvId));
    }

    private Cv getOwnedCv(Long candidateId, Long cvId) {
        if (candidateId == null) {
            return cvRepository.findById(cvId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy CV"
                    ));
        }
        return cvRepository.findByIdAndCandidate_Id(cvId, candidateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy CV thuộc ứng viên này"
                ));
    }

    private CvAnalysis getAnalysis(Long analysisId, Long cvId) {
        return analysisRepository.findByIdAndCv_Id(analysisId, cvId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy kết quả phân tích của CV này"
                ));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
