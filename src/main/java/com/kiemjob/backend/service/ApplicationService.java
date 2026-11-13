package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.application.ApplicationAiScoreResponse;
import com.kiemjob.backend.dto.application.ApplicationRequest;
import com.kiemjob.backend.dto.application.ApplicationResponse;
import com.kiemjob.backend.dto.application.ApplicationStatusRequest;
import com.kiemjob.backend.dto.application.BulkApplicationStatusRequest;
import com.kiemjob.backend.dto.application.BulkApplicationStatusResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Application;
import com.kiemjob.backend.entity.ApplicationStatus;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.ApplicationRepository;
import com.kiemjob.backend.repository.CvRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.security.AiRateLimiter;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.email.EmailService;
import com.kiemjob.backend.specification.ApplicationSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobPostRepository jobPostRepository;
    private final CvRepository cvRepository;
    private final com.kiemjob.backend.repository.AccountRepository accountRepository;
    private final CurrentUserService currentUserService;
    private final CvOwnershipService cvOwnershipService;
    private final EmailService emailService;
    private final CvAnalysisService cvAnalysisService;
    private final AiRateLimiter aiRateLimiter;

    public ApplicationResponse apply(
            Long jobPostId,
            ApplicationRequest request) {

        Candidate candidate = currentUserService.requireCandidate();

        // Kiểm tra CV thuộc về ứng viên đang đăng nhập
        cvOwnershipService.checkOwnership(request.getCvId(), candidate.getAccountId());

        JobPost jobPost = jobPostRepository.findById(jobPostId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy tin tuyển dụng"));

        if (!"published".equalsIgnoreCase(jobPost.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Tin tuyển dụng chưa được công khai");
        }

        if (applicationRepository.existsByJobPost_IdAndCv_Id(jobPostId, request.getCvId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Bạn đã nộp CV này cho tin tuyển dụng");
        }

        Application application = new Application();
        application.setJobPost(jobPost);
        application.setCv(cvRepository.getReferenceById(request.getCvId()));
        application.setCoverLatter(normalize(request.getCoverLatter()));
        application.setStatus(ApplicationStatus.PENDING.getValue());

        try {
            Application saved = applicationRepository.saveAndFlush(application);
            return ApplicationResponse.fromEntity(saved, accountRepository);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "CV này đã ứng tuyển vào tin tuyển dụng");
        }
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getApplicationsForCurrentUser(Long cvId, Long candidateId) {
        if (currentUserService.isAdmin()) {
            if (cvId != null) {
                return applicationRepository
                    .findByCv_IdOrderByAppliedAtDesc(cvId)
                    .stream()
                    .map(a -> ApplicationResponse.fromEntity(a, accountRepository))
                    .toList();
            }
            if (candidateId != null) {
                return applicationRepository
                    .findByCv_Candidate_IdOrderByAppliedAtDesc(candidateId)
                    .stream()
                    .map(a -> ApplicationResponse.fromEntity(a, accountRepository))
                    .toList();
            }
            return applicationRepository
                .findAll()
                .stream()
                .map(a -> ApplicationResponse.fromEntity(a, accountRepository))
                .toList();
        }

        Candidate candidate = currentUserService.requireCandidate();
        if (cvId != null) {
            cvOwnershipService.checkOwnership(cvId, candidate.getAccountId());
            return applicationRepository
                .findByCv_IdOrderByAppliedAtDesc(cvId)
                .stream()
                .map(a -> ApplicationResponse.fromEntity(a, accountRepository))
                .toList();
        }

        return applicationRepository
            .findByCv_Candidate_AccountIdOrderByAppliedAtDesc(candidate.getAccountId())
            .stream()
            .map(a -> ApplicationResponse.fromEntity(a, accountRepository))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getByJobPost(Long jobPostId) {
        JobPost jobPost = jobPostRepository.findById(jobPostId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy tin tuyển dụng"));

        if (!currentUserService.isAdmin()) {
            Recruiter recruiter = currentUserService.requireRecruiter();
            if (jobPost.getCompany() == null || recruiter.getCompanyId() == null
                    || !jobPost.getCompany().getId().equals(recruiter.getCompanyId())) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền xem danh sách ứng tuyển của tin này");
            }
        }

        return applicationRepository
            .findByJobPost_IdOrderByAppliedAtDesc(jobPostId)
            .stream()
            .map(a -> ApplicationResponse.fromEntity(a, accountRepository))
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> getByJobPostPaged(
            Long jobPostId,
            String status,
            String keyword,
            Pageable pageable
    ) {
        JobPost jobPost = jobPostRepository.findById(jobPostId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy tin tuyển dụng"));

        if (!currentUserService.isAdmin()) {
            Recruiter recruiter = currentUserService.requireRecruiter();
            if (jobPost.getCompany() == null || recruiter.getCompanyId() == null
                    || !jobPost.getCompany().getId().equals(recruiter.getCompanyId())) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền xem danh sách ứng tuyển của tin này");
            }
        }

        Specification<Application> spec = ApplicationSpecification.filterApplications(
                jobPostId,
                null,
                status,
                keyword
        );

        return applicationRepository.findAll(spec, pageable)
                .map(a -> ApplicationResponse.fromEntity(a, accountRepository));
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> getApplicationsForRecruiter(
            Long jobId,
            String status,
            String keyword,
            Pageable pageable
    ) {
        Long companyId = null;
        if (!currentUserService.isAdmin()) {
            Recruiter recruiter = currentUserService.requireRecruiter();
            companyId = recruiter.getCompanyId();
            if (companyId == null) {
                return Page.empty(pageable);
            }
        }

        Specification<Application> spec = ApplicationSpecification.filterApplications(
                jobId,
                companyId,
                status,
                keyword
        );

        return applicationRepository.findAll(spec, pageable)
                .map(a -> ApplicationResponse.fromEntity(a, accountRepository));
    }

    public ApplicationResponse getById(Long id) {
        Application application = applicationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ ứng tuyển"));

        if (currentUserService.isAdmin()) {
            return ApplicationResponse.fromEntity(application, accountRepository);
        }

        Account account = currentUserService.requireAccount();

        // Ứng viên chủ đơn
        if ("candidate".equalsIgnoreCase(account.getRole())) {
            if (application.getCv() != null && application.getCv().getCandidate() != null
                    && account.getId().equals(application.getCv().getCandidate().getAccountId())) {
                return ApplicationResponse.fromEntity(application, accountRepository);
            }
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ ứng tuyển");
        }

        // Nhà tuyển dụng sở hữu bài
        if ("recruiter".equalsIgnoreCase(account.getRole())) {
            Recruiter recruiter = currentUserService.requireRecruiter();
            if (application.getJobPost() != null && application.getJobPost().getCompany() != null
                    && recruiter.getCompanyId() != null
                    && recruiter.getCompanyId().equals(application.getJobPost().getCompany().getId())) {

                // Mở CV lần đầu: tự động chuyển pending -> viewed ở backend
                if (ApplicationStatus.PENDING.getValue().equalsIgnoreCase(application.getStatus())) {
                    application.setStatus(ApplicationStatus.VIEWED.getValue());
                    application = applicationRepository.save(application);
                    sendNotificationEmail(application, ApplicationStatus.VIEWED.getValue());
                }

                return ApplicationResponse.fromEntity(application, accountRepository);
            }
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ ứng tuyển");
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập hồ sơ này");
    }

    public ApplicationResponse updateStatus(
            Long id,
            ApplicationStatusRequest request) {

        Application application = applicationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ ứng tuyển"));

        if (!currentUserService.isAdmin()) {
            Recruiter recruiter = currentUserService.requireRecruiter();
            if (application.getJobPost() == null || application.getJobPost().getCompany() == null
                    || recruiter.getCompanyId() == null
                    || !recruiter.getCompanyId().equals(application.getJobPost().getCompany().getId())) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền cập nhật trạng thái đơn ứng tuyển này");
            }
        }

        ApplicationStatus currentStatus = ApplicationStatus.fromValue(application.getStatus());
        ApplicationStatus targetStatus = ApplicationStatus.fromValue(request.getStatus());

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Không thể chuyển trạng thái từ '" + currentStatus.getLabel() + "' sang '" + targetStatus.getLabel() + "'. Vui lòng thao tác theo đúng quy trình tuyển dụng.");
        }

        String oldStatus = application.getStatus();
        application.setStatus(targetStatus.getValue());

        Application saved = applicationRepository.save(application);

        // Gửi email thông báo cho ứng viên khi trạng thái đơn ứng tuyển thay đổi
        if (!targetStatus.getValue().equalsIgnoreCase(oldStatus)) {
            sendNotificationEmail(saved, targetStatus.getValue());
        }

        return ApplicationResponse.fromEntity(saved, accountRepository);
    }

    public BulkApplicationStatusResponse bulkUpdateStatus(BulkApplicationStatusRequest request) {
        if (!currentUserService.isAdmin() && !currentUserService.isRecruiter()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền cập nhật trạng thái đơn ứng tuyển");
        }

        Recruiter recruiter = currentUserService.isAdmin() ? null : currentUserService.requireRecruiter();
        Long companyId = recruiter != null ? recruiter.getCompanyId() : null;

        ApplicationStatus targetStatus = ApplicationStatus.fromValue(request.getStatus());
        List<Application> apps = applicationRepository.findByIdIn(request.getIds());

        List<Long> updatedIds = new ArrayList<>();
        for (Application app : apps) {
            // Kiểm tra quyền sở hữu đối với từng đơn
            if (recruiter != null) {
                if (app.getJobPost() == null || app.getJobPost().getCompany() == null
                        || companyId == null || !companyId.equals(app.getJobPost().getCompany().getId())) {
                    continue; // Bỏ qua đơn không thuộc công ty mình
                }
            }

            ApplicationStatus currentStatus;
            try {
                currentStatus = ApplicationStatus.fromValue(app.getStatus());
            } catch (Exception ex) {
                currentStatus = ApplicationStatus.PENDING;
            }

            if (currentStatus.canTransitionTo(targetStatus)) {
                String oldStatus = app.getStatus();
                app.setStatus(targetStatus.getValue());
                applicationRepository.save(app);
                updatedIds.add(app.getId());

                if (!targetStatus.getValue().equalsIgnoreCase(oldStatus)) {
                    sendNotificationEmail(app, targetStatus.getValue());
                }
            }
        }

        return BulkApplicationStatusResponse.builder()
                .updatedCount(updatedIds.size())
                .updatedIds(updatedIds)
                .status(targetStatus.getValue())
                .message("Đã cập nhật thành công " + updatedIds.size() + "/" + request.getIds().size() + " đơn ứng tuyển sang '" + targetStatus.getLabel() + "'")
                .build();
    }

    public ApplicationAiScoreResponse scoreApplicationAgainstJob(Long id) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ ứng tuyển"));

        if (!currentUserService.isAdmin()) {
            Recruiter recruiter = currentUserService.requireRecruiter();
            if (application.getJobPost() == null || application.getJobPost().getCompany() == null
                    || recruiter.getCompanyId() == null
                    || !recruiter.getCompanyId().equals(application.getJobPost().getCompany().getId())) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền yêu cầu chấm điểm cho hồ sơ này");
            }
        }

        // Chấm điểm on-demand thông qua CvAnalysisService
        return cvAnalysisService.scoreApplicationAgainstJob(id);
    }

    private void sendNotificationEmail(Application application, String newStatus) {
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
                    String jobTitle = (application.getJobPost() != null && application.getJobPost().getTitle() != null)
                            ? application.getJobPost().getTitle()
                            : "Vị trí tuyển dụng";
                    String companyName = (application.getJobPost() != null && application.getJobPost().getCompany() != null
                            && application.getJobPost().getCompany().getName() != null)
                            ? application.getJobPost().getCompany().getName()
                            : "Doanh nghiệp tuyển dụng";

                    emailService.sendApplicationStatusUpdate(
                            email,
                            candidateName,
                            jobTitle,
                            companyName,
                            newStatus
                    );
                    log.info("Sent application status update email to {} for application #{}", email, application.getId());
                }
            });
        } catch (Exception ex) {
            log.error("Không thể gửi email thông báo trạng thái cho đơn #{}: {}", application.getId(), ex.getMessage());
        }
    }

    public void withdraw(Long id) {
        Application application = applicationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ ứng tuyển"));

        Candidate candidate = currentUserService.requireCandidate();

        if (application.getCv() == null || application.getCv().getCandidate() == null
                || !candidate.getId().equals(application.getCv().getCandidate().getId())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Bạn không có quyền rút đơn ứng tuyển này");
        }

        if (!ApplicationStatus.PENDING.getValue().equalsIgnoreCase(application.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Chỉ có thể rút đơn khi đang ở trạng thái chờ xử lý (pending)");
        }

        applicationRepository.delete(application);
    }

    private String normalize(String str) {
        if (str == null) return null;
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
