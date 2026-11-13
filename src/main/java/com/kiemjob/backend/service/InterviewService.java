package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.interview.InterviewRequest;
import com.kiemjob.backend.dto.interview.InterviewResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Application;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.Interview;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.ApplicationRepository;
import com.kiemjob.backend.repository.InterviewRepository;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.email.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final AccountRepository accountRepository;
    private final CurrentUserService currentUserService;
    private final EmailService emailService;

    // ─── Tạo lịch phỏng vấn ───

    public InterviewResponse create(Long applicationId, InterviewRequest request) {

        Recruiter recruiter = currentUserService.requireRecruiter();

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ ứng tuyển"));

        // Kiểm tra quyền: recruiter cùng công ty với tin tuyển dụng
        checkCompanyOwnership(recruiter, application);

        // 1. Trả 409 nếu đơn đang ở trạng thái rejected hoặc accepted
        String currentStatus = application.getStatus();
        if ("rejected".equalsIgnoreCase(currentStatus) || "accepted".equalsIgnoreCase(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể đặt lịch phỏng vấn cho hồ sơ đã ở trạng thái: " + currentStatus);
        }

        // 2. Trả 400 nếu scheduledAt không ở tương lai (múi giờ Asia/Ho_Chi_Minh)
        ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime nowVn = LocalDateTime.now(vnZone);
        if (request.scheduledAt() == null || !request.scheduledAt().isAfter(nowVn)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Thời gian phỏng vấn phải ở tương lai");
        }

        Interview interview = new Interview();
        interview.setApplication(application);
        interview.setRecruiter(recruiter);
        interview.setTitle(request.title());
        interview.setScheduledAt(request.scheduledAt());
        interview.setNote(request.note());

        Interview saved = interviewRepository.save(interview);

        // Cập nhật trạng thái application → "interview" nếu chưa phải
        if (!"interview".equals(currentStatus)) {
            application.setStatus("interview");
            applicationRepository.save(application);
        }

        // Gửi email cho ứng viên MỖI LẦN tạo lịch mới (nội dung có giờ hẹn và ghi chú nơi gặp)
        sendInterviewNotification(application, saved.getScheduledAt(), saved.getNote());

        return toResponse(saved);
    }

    // ─── Lấy danh sách lịch theo hồ sơ ứng tuyển ───

    @Transactional(readOnly = true)
    public List<InterviewResponse> getByApplication(Long applicationId) {

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ ứng tuyển"));

        requireAccessTo(application);

        return interviewRepository.findByApplicationIdWithDetails(applicationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ─── Lấy tất cả lịch phỏng vấn của công ty ───

    @Transactional(readOnly = true)
    public List<InterviewResponse> getMyCompanyInterviews() {

        Recruiter recruiter = currentUserService.requireRecruiter();

        return interviewRepository
                .findByRecruiterCompanyIdWithDetails(recruiter.getCompanyId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ─── Xem chi tiết lịch phỏng vấn ───

    @Transactional(readOnly = true)
    public InterviewResponse getById(Long id) {

        Interview interview = interviewRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy lịch phỏng vấn"));

        requireAccessTo(interview.getApplication());

        return toResponse(interview);
    }

    // ─── Cập nhật lịch phỏng vấn ───

    public InterviewResponse update(Long id, InterviewRequest request) {

        Interview interview = interviewRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy lịch phỏng vấn"));

        requireAccessTo(interview.getApplication());

        interview.setTitle(request.title());
        interview.setScheduledAt(request.scheduledAt());
        interview.setNote(request.note());

        return toResponse(interviewRepository.save(interview));
    }

    // ─── Xóa lịch phỏng vấn ───

    public void delete(Long id) {

        Interview interview = interviewRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy lịch phỏng vấn"));

        requireAccessTo(interview.getApplication());

        interviewRepository.delete(interview);
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Kiểm tra quyền truy cập:
     * - Admin bypass toàn bộ.
     * - Ứng viên chỉ xem được lịch của chính đơn ứng tuyển do mình nộp.
     * - Nhà tuyển dụng cùng công ty với tin tuyển dụng.
     */
    private void requireAccessTo(Application application) {
        if (currentUserService.isAdmin()) {
            return;
        }

        Account account = currentUserService.requireAccount();

        // Nếu là ứng viên: kiểm tra có đúng là chủ sở hữu đơn ứng tuyển không
        if ("candidate".equalsIgnoreCase(account.getRole())) {
            if (application.getCv() != null && application.getCv().getCandidate() != null
                    && account.getId().equals(application.getCv().getCandidate().getAccountId())) {
                return;
            }
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền xem thông tin lịch phỏng vấn này");
        }

        Recruiter recruiter = currentUserService.requireRecruiter();
        checkCompanyOwnership(recruiter, application);
    }

    private void checkCompanyOwnership(Recruiter recruiter, Application application) {
        JobPost jobPost = application.getJobPost();
        if (jobPost == null || jobPost.getCompany() == null
                || recruiter.getCompanyId() == null
                || !recruiter.getCompanyId().equals(jobPost.getCompany().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thao tác trên hồ sơ ứng tuyển này");
        }
    }

    /**
     * Gửi email thông báo cho ứng viên khi có lịch phỏng vấn mới.
     * Nội dung bao gồm giờ hẹn (scheduledAt) và ghi chú nơi gặp (note).
     */
    private void sendInterviewNotification(Application application, LocalDateTime scheduledAt, String note) {
        try {
            if (application.getCv() == null || application.getCv().getCandidate() == null) {
                return;
            }
            Long accountId = application.getCv().getCandidate().getAccountId();
            if (accountId == null) {
                return;
            }
            accountRepository.findById(accountId).ifPresent(account -> {
                String email = account.getEmail();
                if (email != null && !email.isBlank()) {
                    String candidateName = application.getCv().getCandidate().getFullname();
                    String jobTitle = (application.getJobPost() != null)
                            ? application.getJobPost().getTitle()
                            : "Vị trí tuyển dụng";
                    String companyName = (application.getJobPost() != null
                            && application.getJobPost().getCompany() != null)
                            ? application.getJobPost().getCompany().getName()
                            : "Doanh nghiệp tuyển dụng";

                    emailService.sendInterviewInvitation(
                            email, candidateName, jobTitle, companyName, scheduledAt, note);
                    log.info("Đã gửi email mời phỏng vấn tới {} cho đơn #{}",
                            email, application.getId());
                }
            });
        } catch (Exception ex) {
            log.error("Không thể gửi email mời phỏng vấn cho đơn #{}: {}",
                    application.getId(), ex.getMessage());
        }
    }

    /**
     * Chuyển Entity → Response DTO, populate đầy đủ thông tin liên quan.
     */
    private InterviewResponse toResponse(Interview interview) {

        String recruiterName = null;
        String candidateName = null;
        String candidateEmail = null;
        String jobTitle = null;

        // Recruiter name
        if (interview.getRecruiter() != null) {
            recruiterName = interview.getRecruiter().getFullName();
        }

        // Candidate info (qua Application → Cv → Candidate → Account)
        if (interview.getApplication() != null
                && interview.getApplication().getCv() != null
                && interview.getApplication().getCv().getCandidate() != null) {

            Candidate candidate = interview.getApplication().getCv().getCandidate();
            candidateName = candidate.getFullname();

            if (candidate.getAccountId() != null) {
                candidateEmail = accountRepository.findById(candidate.getAccountId())
                        .map(a -> a.getEmail())
                        .orElse(null);
            }
        }

        // Job title
        if (interview.getApplication() != null
                && interview.getApplication().getJobPost() != null) {
            jobTitle = interview.getApplication().getJobPost().getTitle();
        }

        return new InterviewResponse(
                interview.getId(),
                interview.getApplication() != null ? interview.getApplication().getId() : null,
                interview.getRecruiter() != null ? interview.getRecruiter().getId() : null,
                recruiterName,
                candidateName,
                candidateEmail,
                jobTitle,
                interview.getTitle(),
                interview.getScheduledAt(),
                interview.getNote(),
                interview.getCreatedAt());
    }
}
