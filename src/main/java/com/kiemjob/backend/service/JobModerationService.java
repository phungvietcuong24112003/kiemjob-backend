package com.kiemjob.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kiemjob.backend.dto.ai.AiLabelResult;
import com.kiemjob.backend.dto.ai.AiModerationResponse;
import com.kiemjob.backend.dto.moderation.JobModerationResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.JobDetail;
import com.kiemjob.backend.entity.JobModeration;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.JobDetailRepository;
import com.kiemjob.backend.repository.JobModerationRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.repository.RecruiterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class JobModerationService {

    private static final Logger log = LoggerFactory.getLogger(JobModerationService.class);

    private final JobPostRepository jobPostRepository;
    private final JobDetailRepository jobDetailRepository;
    private final JobModerationRepository jobModerationRepository;
    private final AiModerationService aiModerationService;
    private final ObjectMapper objectMapper;
    private final AccountRepository accountRepository;
    private final RecruiterRepository recruiterRepository;

    @org.springframework.beans.factory.annotation.Value("${moderation.max-checks-per-hour:5}")
    private int maxChecksPerHour = 5;

    public JobModerationService(
            JobPostRepository jobPostRepository,
            JobDetailRepository jobDetailRepository,
            JobModerationRepository jobModerationRepository,
            AiModerationService aiModerationService,
            ObjectMapper objectMapper,
            AccountRepository accountRepository,
            RecruiterRepository recruiterRepository) {
        this.jobPostRepository = jobPostRepository;
        this.jobDetailRepository = jobDetailRepository;
        this.jobModerationRepository = jobModerationRepository;
        this.aiModerationService = aiModerationService;
        this.objectMapper = objectMapper;
        this.accountRepository = accountRepository;
        this.recruiterRepository = recruiterRepository;
    }

    /**
     * Chạy kiểm duyệt AI cho bài tuyển dụng với kiểm tra quyền
     */
    @Transactional
    public JobModerationResponse checkJob(Long jobId, String userEmail) {
        // 1. Lấy bài tuyển dụng
        JobPost jobPost = jobPostRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài tuyển dụng ID: " + jobId
                ));

        // 2. Xác thực và kiểm tra quyền truy cập của người dùng
        Account account = validateAccess(jobPost, userEmail);
        boolean isAdmin = "admin".equalsIgnoreCase(account.getRole());

        // 2.1 Giới hạn tần suất kiểm duyệt cho mỗi tin tuyển dụng (Admin được miễn trừ để có thể kiểm thử và chạy lại kiểm duyệt)
        if (!isAdmin) {
            java.time.LocalDateTime oneHourAgo = java.time.LocalDateTime.now().minusHours(1);
            long recentChecks = jobModerationRepository.countByJobPost_IdAndCreatedAtAfter(jobId, oneHourAgo);
            if (recentChecks >= maxChecksPerHour) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Bài tuyển dụng ID " + jobId + " đã vượt quá giới hạn " + maxChecksPerHour + " lần kiểm duyệt trong 1 giờ. Vui lòng thử lại sau."
                );
            }
        }

        // 3. Lấy toàn bộ nội dung chi tiết
        List<JobDetail> details = jobDetailRepository.findByJobPost_IdOrderByIdAsc(jobId);
        if (details.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bài tuyển dụng chưa có nội dung chi tiết để kiểm duyệt"
            );
        }

        // 4. Ghép nội dung các phần thành description (chỉ lấy tiêu đề và nội dung chi tiết, không kèm thông tin nhạy cảm của nhà tuyển dụng)
        StringBuilder description = new StringBuilder();
        for (JobDetail detail : details) {
            description.append("[").append(detail.getTitle()).append("]\n");
            if (detail.getContent() != null) {
                description.append(detail.getContent());
            }
            description.append("\n\n");
        }

        // 5. Chuẩn bị thực thể kiểm duyệt
        JobModeration moderation = new JobModeration();
        moderation.setJobPost(jobPost);

        try {
            // 6. Gọi Groq AI qua AiModerationService
            AiModerationResponse aiResponse = aiModerationService.moderate(
                    jobPost.getTitle(),
                    description.toString()
            );

            // 7. Chuyển kết quả AI thành JsonNode của Jackson
            JsonNode result = objectMapper.valueToTree(aiResponse);
            moderation.setResult(result);

            // 8. Xác định trạng thái kiểm duyệt
            boolean hasRisk = aiResponse != null
                    && aiResponse.results() != null
                    && aiResponse.results().values().stream().anyMatch(this::hasRiskPrediction);

            moderation.setModerationStatus(hasRisk ? "pending_review" : "passed");

        } catch (Exception exception) {
            log.error("Lỗi khi kết nối hoặc xử lý kết quả từ Groq AI cho job {}: {}", jobId, exception.getMessage());

            ObjectNode errorResult = objectMapper.createObjectNode();
            String errorMsg = exception.getMessage() != null
                    ? exception.getMessage()
                    : exception.getClass().getSimpleName();
            errorResult.put("error", sanitizeErrorMessage(errorMsg));

            // Fail-safe: Luôn gán status là error và KHÔNG cho bài đăng tự động qua khi xảy ra sự cố
            moderation.setResult(errorResult);
            moderation.setModerationStatus("error");
        }

        // 9. Lưu kết quả kiểm duyệt vào PostgreSQL JSONB
        JobModeration saved = jobModerationRepository.save(moderation);
        return JobModerationResponse.fromEntity(saved);
    }

    /**
     * Overload checkJob không truyền email (tự lấy từ SecurityContextHolder nếu có)
     */
    @Transactional
    public JobModerationResponse checkJob(Long jobId) {
        String email = getCurrentAuthEmail();
        return checkJob(jobId, email);
    }

    /**
     * Lấy lịch sử kiểm duyệt của một bài tuyển dụng
     */
    @Transactional(readOnly = true)
    public List<JobModerationResponse> getHistory(Long jobId, String userEmail) {
        JobPost jobPost = jobPostRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài tuyển dụng ID: " + jobId
                ));

        validateAccess(jobPost, userEmail);

        return jobModerationRepository
                .findByJobPost_IdOrderByCreatedAtDescIdDesc(jobId)
                .stream()
                .map(JobModerationResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobModerationResponse> getHistory(Long jobId) {
        return getHistory(jobId, getCurrentAuthEmail());
    }

    /**
     * Lấy lần kiểm duyệt gần nhất
     */
    @Transactional(readOnly = true)
    public JobModerationResponse getLatest(Long jobId, String userEmail) {
        JobPost jobPost = jobPostRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài tuyển dụng ID: " + jobId
                ));

        validateAccess(jobPost, userEmail);

        return jobModerationRepository
                .findFirstByJobPost_IdOrderByCreatedAtDescIdDesc(jobId)
                .map(JobModerationResponse::fromEntity)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Bài tuyển dụng chưa được kiểm duyệt"
                ));
    }

    @Transactional(readOnly = true)
    public JobModerationResponse getLatest(Long jobId) {
        return getLatest(jobId, getCurrentAuthEmail());
    }

    /**
     * Kiểm tra quyền:
     * - Admin: Có quyền trên mọi bài tuyển dụng.
     * - Recruiter: Chỉ có quyền trên bài tuyển dụng do chính mình đăng hoặc thuộc công ty của mình.
     * - Khác/Chưa đăng nhập: Ném lỗi 401 hoặc 403.
     */
    private Account validateAccess(JobPost jobPost, String email) {
        if (email == null || email.isBlank() || "anonymousUser".equalsIgnoreCase(email)) {
            // Thử lấy lại từ SecurityContext
            email = getCurrentAuthEmail();
        }

        if (email == null || email.isBlank() || "anonymousUser".equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để thực hiện thao tác");
        }

        Account account = accountRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"));

        if ("admin".equalsIgnoreCase(account.getRole())) {
            return account; // Admin có toàn quyền
        }

        if ("recruiter".equalsIgnoreCase(account.getRole())) {
            Recruiter recruiter = recruiterRepository.findByAccountId(account.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Không tìm thấy hồ sơ nhà tuyển dụng"));

            boolean isOwner = false;
            // So sánh ID người đăng
            if (jobPost.getPostedBy() != null && jobPost.getPostedBy().getId().equals(recruiter.getId())) {
                isOwner = true;
            }
            // So sánh Company ID
            if (!isOwner && jobPost.getCompany() != null && recruiter.getCompanyId() != null
                    && jobPost.getCompany().getId().equals(recruiter.getCompanyId())) {
                isOwner = true;
            }

            if (!isOwner) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Bạn không có quyền quản lý bài tuyển dụng này"
                );
            }
            return account;
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Chỉ nhà tuyển dụng sở hữu bài đăng hoặc Quản trị viên mới có quyền kiểm duyệt"
        );
    }

    private boolean hasRiskPrediction(AiLabelResult result) {
        return result != null && result.prediction() == 1;
    }

    private String sanitizeErrorMessage(String errorMsg) {
        if (errorMsg == null) {
            return "Lỗi không xác định khi kiểm duyệt AI";
        }
        String sanitized = errorMsg.replaceAll("(?i)bearer\\s+[a-zA-Z0-9_\\-\\.]+", "Bearer [PROTECTED]")
                .replaceAll("(?i)gsk_[a-zA-Z0-9]+", "[PROTECTED_KEY]");
        if (sanitized.length() > 300) {
            sanitized = sanitized.substring(0, 300) + "...";
        }
        return sanitized;
    }

    private String getCurrentAuthEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return auth.getName();
        }
        return null;
    }
}
