package com.kiemjob.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kiemjob.backend.ai.util.AiTextSanitizer;
import com.kiemjob.backend.ai.util.JsonResponseParser;
import com.kiemjob.backend.ai.util.PromptTemplates;
import com.kiemjob.backend.entity.Cv;
import com.kiemjob.backend.entity.CvAnalysis;
import com.kiemjob.backend.repository.CvAnalysisRepository;
import com.kiemjob.backend.repository.CvRepository;
import com.kiemjob.backend.service.ai.GroqClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service chuyên dụng xử lý tự động phân tích CV bằng AI ở chế độ nền.
 *
 * NGUYÊN TẮC THIẾT KẾ QUAN TRỌNG:
 * 1. Tuyệt đối không mở @Transactional bao quanh quá trình đọc file từ S3 và gọi Groq AI.
 * 2. Chỉ sử dụng TransactionTemplate ngắn để đọc hoặc ghi trạng thái vào cơ sở dữ liệu.
 * 3. Bỏ qua phân tích lại nếu content_hash và prompt_version không đổi và đã completed.
 * 4. Không áp dụng AiRateLimiter cho tác vụ chạy nền.
 */
@Slf4j
@Service
public class CvAutoAnalysisService {

    private final CvRepository cvRepository;
    private final CvAnalysisRepository cvAnalysisRepository;
    private final CvAnalysisService cvAnalysisService;
    private final GroqClient groqClient;
    private final PromptTemplates promptTemplates;
    private final JsonResponseParser jsonResponseParser;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CvAutoAnalysisService(
            CvRepository cvRepository,
            CvAnalysisRepository cvAnalysisRepository,
            CvAnalysisService cvAnalysisService,
            GroqClient groqClient,
            PromptTemplates promptTemplates,
            JsonResponseParser jsonResponseParser,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.cvRepository = cvRepository;
        this.cvAnalysisRepository = cvAnalysisRepository;
        this.cvAnalysisService = cvAnalysisService;
        this.groqClient = groqClient;
        this.promptTemplates = promptTemplates;
        this.jsonResponseParser = jsonResponseParser;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Thực hiện phân tích CV nền (chạy trong worker thread của aiTaskExecutor).
     */
    public void analyzeCvAsync(Long cvId) {
        if (cvId == null) return;

        // 1. Kiểm tra CV và đảm bảo CV đang ở chế độ public (Transaction ngắn)
        Cv cv = transactionTemplate.execute(status -> cvRepository.findById(cvId).orElse(null));
        if (cv == null) {
            log.warn("Không tìm thấy CV ID {} để phân tích tự động", cvId);
            return;
        }

        if (!Boolean.TRUE.equals(cv.getIsPublic())) {
            log.info("CV ID {} hiện không ở trạng thái public. Bỏ qua phân tích nền.", cvId);
            return;
        }

        CvAnalysis existingAnalysis = transactionTemplate.execute(status ->
                cvAnalysisRepository.findByCv_Id(cvId).orElse(null)
        );

        // 2. Trích xuất text từ CV (NGOÀI TRANSACTION)
        String cvText;
        try {
            cvText = cvAnalysisService.extractCvText(cvId, cv);
        } catch (Exception ex) {
            log.error("Lỗi khi trích xuất text cho CV ID {}: {}", cvId, ex.getMessage());
            markFailed(cvId, "Lỗi khi trích xuất file PDF: " + sanitizeError(ex), getAttemptCount(existingAnalysis) + 1);
            return;
        }

        // Kiểm tra file PDF scan / không có văn bản có thể chọn (< 100 ký tự)
        if (cvText == null || cvText.trim().length() < 100) {
            log.warn("CV ID {} không đọc được văn bản (text rỗng hoặc < 100 ký tự - có thể là PDF scan)", cvId);
            markFailed(cvId, "NO_TEXT", 0);
            return;
        }

        // 3. Tính content_hash (SHA-256)
        String newContentHash = sha256Hex(cvText.trim());

        // Kiểm tra nếu nội dung và prompt_version không đổi và đã hoàn tất trước đó
        if (existingAnalysis != null
                && "completed".equalsIgnoreCase(existingAnalysis.getAnalysisStatus())
                && newContentHash.equals(existingAnalysis.getContentHash())
                && CvAnalysisService.PROMPT_VERSION.equals(existingAnalysis.getPromptVersion())) {
            log.info("CV ID {} có content_hash và prompt_version không đổi, đã hoàn tất trước đó. Bỏ qua gọi AI.", cvId);
            return;
        }

        // 4. Cập nhật trạng thái 'processing' (Transaction ngắn)
        transactionTemplate.executeWithoutResult(status -> {
            CvAnalysis analysis = cvAnalysisRepository.findByCv_Id(cvId)
                    .orElseGet(() -> CvAnalysis.builder().cv(cv).build());
            analysis.setAnalysisStatus("processing");
            analysis.setLastError(null);
            analysis.setUpdatedAt(LocalDateTime.now());
            cvAnalysisRepository.save(analysis);
        });

        // 5. Chuẩn bị prompt và gọi Groq AI (NGOÀI TRANSACTION)
        String truncatedCvText = cvAnalysisService.truncateAtLineBoundary(cvText, 10000);
        String maskedCvText = AiTextSanitizer.maskPii(truncatedCvText);

        String title = (cv.getTitle() != null && !cv.getTitle().isBlank())
                ? cv.getTitle().trim()
                : "Vị trí chuyên môn theo định hướng CV";

        String systemPrompt = promptTemplates.render("cv-scoring-system", Map.of(
                "jobTitle", title,
                "jobLevel", "Tương ứng với kinh nghiệm",
                "jobDescription", "Đánh giá mức độ hoàn thiện, chuyên nghiệp và tính cạnh tranh của hồ sơ ứng viên trên thị trường lao động",
                "jobRequirements", "Kỹ năng chuyên môn vững vàng, kinh nghiệm thực tế rõ ràng, học vấn và chứng chỉ phù hợp"
        ));
        String userPrompt = promptTemplates.render("cv-scoring-user", Map.of(
                "cvText", maskedCvText
        ));

        log.info("Bắt đầu gọi AI tự động phân tích cho CV ID: {}", cvId);
        String rawJson;
        try {
            rawJson = groqClient.generateJson(systemPrompt, userPrompt);
        } catch (Exception ex) {
            log.error("Lỗi khi gọi Groq AI để phân tích tự động CV ID {}: {}", cvId, ex.getMessage());
            markFailed(cvId, "Dịch vụ AI tạm thời không khả dụng: " + sanitizeError(ex), getAttemptCount(existingAnalysis) + 1);
            return;
        }

        // 6. Phân tích cú pháp JSON và chuẩn hóa (NGOÀI TRANSACTION)
        ObjectNode normalizedNode;
        try {
            String cleanJson = jsonResponseParser.cleanJson(rawJson);
            JsonNode rootNode = objectMapper.readTree(cleanJson);
            normalizedNode = cvAnalysisService.normalizeAnalysis(rootNode, "pdf");
        } catch (Exception ex) {
            log.error("Lỗi parse hoặc validate JSON AI trả về cho CV ID {}: {}", cvId, ex.getMessage());
            markFailed(cvId, "Dữ liệu AI trả về không đúng định dạng: " + sanitizeError(ex), getAttemptCount(existingAnalysis) + 1);
            return;
        }

        BigDecimal qualityScore = BigDecimal.valueOf(normalizedNode.get("overallScore").asInt());
        String suggestions = cvAnalysisService.buildSuggestionsText(normalizedNode);
        String summary = normalizedNode.path("summary").asText("").trim();

        // 7. Lưu kết quả hoàn tất vào DB (Transaction ngắn)
        transactionTemplate.executeWithoutResult(status -> {
            CvAnalysis analysis = cvAnalysisRepository.findByCv_Id(cvId)
                    .orElseGet(() -> CvAnalysis.builder().cv(cv).build());
            analysis.setParsedData(normalizedNode);
            analysis.setSummary(summary.isEmpty() ? null : summary);
            analysis.setQualityScore(qualityScore);
            analysis.setSuggestions(suggestions);
            analysis.setAnalysisStatus("completed");
            analysis.setContentHash(newContentHash);
            analysis.setPromptVersion(CvAnalysisService.PROMPT_VERSION);
            analysis.setAttemptCount(0);
            analysis.setLastError(null);
            analysis.setAnalyzedAt(LocalDateTime.now());
            analysis.setUpdatedAt(LocalDateTime.now());
            cvAnalysisRepository.save(analysis);
        });

        log.info("Phân tích AI tự động thành công cho CV ID: {}, QualityScore: {}", cvId, qualityScore);
    }

    /**
     * Cập nhật trạng thái failed trong transaction ngắn.
     */
    public void markFailed(Long cvId, String errorMessage, int nextAttemptCount) {
        transactionTemplate.executeWithoutResult(status -> {
            Cv cv = cvRepository.findById(cvId).orElse(null);
            if (cv == null) return;
            CvAnalysis analysis = cvAnalysisRepository.findByCv_Id(cvId)
                    .orElseGet(() -> CvAnalysis.builder().cv(cv).build());
            analysis.setAnalysisStatus("failed");
            String safeError = errorMessage != null && errorMessage.length() > 500
                    ? errorMessage.substring(0, 497) + "..."
                    : errorMessage;
            analysis.setLastError(safeError);
            analysis.setAttemptCount(nextAttemptCount);
            analysis.setUpdatedAt(LocalDateTime.now());
            cvAnalysisRepository.save(analysis);
        });
    }

    private int getAttemptCount(CvAnalysis analysis) {
        return analysis != null ? analysis.getAttemptCount() : 0;
    }

    private String sanitizeError(Throwable t) {
        if (t == null) return "Unknown error";
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = t.getClass().getSimpleName();
        }
        if (msg.contains("<cv>") || msg.contains("</cv>")) {
            msg = "Lỗi phản hồi từ AI khi xử lý nội dung CV";
        }
        if (msg.length() > 500) {
            msg = msg.substring(0, 497) + "...";
        }
        return msg;
    }

    public static String sha256Hex(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tính toán SHA-256", e);
        }
    }
}

