package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.job.JobPostRequest;
import com.kiemjob.backend.dto.job.JobPostResponse;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.JobsCategory;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.CompanyRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.repository.JobsCategoryRepository;
import com.kiemjob.backend.repository.RecruiterRepository;

import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.security.CurrentUserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kiemjob.backend.dto.job.JobSpecializationResponse;
import com.kiemjob.backend.entity.JobPostSpecialization;
import com.kiemjob.backend.entity.JobSpecialization;
import com.kiemjob.backend.repository.JobPostSpecializationRepository;
import com.kiemjob.backend.repository.JobSpecializationRepository;

import com.kiemjob.backend.dto.job.JobAddressResponse;
import com.kiemjob.backend.entity.JobAddress;
import com.kiemjob.backend.repository.JobAddressRepository;
import com.kiemjob.backend.repository.JobModerationRepository;
import com.kiemjob.backend.dto.moderation.JobModerationResponse;
import com.kiemjob.backend.entity.JobModeration;
import com.kiemjob.backend.service.email.EmailService;
import com.kiemjob.backend.specification.JobPostSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class JobPostService {

    private final JobPostRepository jobPostRepository;
    private final CompanyRepository companyRepository;
    private final JobsCategoryRepository jobsCategoryRepository;
    private final RecruiterRepository recruiterRepository;
    private final AccountRepository accountRepository;
    private final JobPostSpecializationRepository postSpecializationRepository;
    private final JobSpecializationRepository jobSpecializationRepository;
    private final JobAddressRepository jobAddressRepository;
    private final JobAddressService jobAddressService;
    private final JobModerationRepository jobModerationRepository;
    private final CurrentUserService currentUserService;
    private final EmailService emailService;

    public JobPostService(
            JobPostRepository jobPostRepository,
            CompanyRepository companyRepository,
            JobsCategoryRepository jobsCategoryRepository,
            RecruiterRepository recruiterRepository,
            AccountRepository accountRepository,
            JobPostSpecializationRepository postSpecializationRepository,
            JobSpecializationRepository jobSpecializationRepository,
            JobAddressRepository jobAddressRepository,
            JobAddressService jobAddressService,
            JobModerationRepository jobModerationRepository,
            CurrentUserService currentUserService,
            EmailService emailService) {
        super();
        this.jobPostRepository = jobPostRepository;
        this.companyRepository = companyRepository;
        this.jobsCategoryRepository = jobsCategoryRepository;
        this.recruiterRepository = recruiterRepository;
        this.accountRepository = accountRepository;
        this.postSpecializationRepository = postSpecializationRepository;
        this.jobSpecializationRepository = jobSpecializationRepository;
        this.jobAddressRepository = jobAddressRepository;
        this.jobAddressService = jobAddressService;
        this.jobModerationRepository = jobModerationRepository;
        this.currentUserService = currentUserService;
        this.emailService = emailService;
    }

    @Transactional
    public JobPostResponse create(JobPostRequest request) {
        validateSalary(request.getSalaryMin(), request.getSalaryMax());

        JobsCategory category = jobsCategoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() -> notFound("Không tìm thấy danh mục"));

        Company company;
        Recruiter postedBy;

        if (currentUserService.isAdmin()) {
            if (request.getCompanyId() == null || request.getPostedById() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu ID công ty hoặc ID nhà tuyển dụng");
            }
            company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> notFound("Không tìm thấy công ty"));
            postedBy = recruiterRepository.findById(request.getPostedById())
                    .orElseThrow(() -> notFound("Không tìm thấy nhà tuyển dụng"));
        } else {
            // Yêu cầu RECRUITER (lấy company/postedBy từ recruiter hiện tại)
            postedBy = currentUserService.requireRecruiter();
            company = companyRepository.findById(postedBy.getCompanyId())
                    .orElseThrow(() -> notFound("Không tìm thấy công ty của nhà tuyển dụng"));
        }

        JobPost jobPost = new JobPost();
        jobPost.setCompany(company);
        jobPost.setCategory(category);
        jobPost.setPostedBy(postedBy);

        applyRequest(jobPost, request);

        // Khi Nhà tuyển dụng tạo tin, bài luôn luôn ở trạng thái 'draft' (chờ admin duyệt)
        // Chỉ Quản trị viên (Admin) mới có quyền tạo trực tiếp bài ở trạng thái 'open'
        if (currentUserService.isAdmin()) {
            String targetStatus = request.getStatus();
            if (targetStatus == null || targetStatus.isBlank()) {
                targetStatus = "draft";
            }
            jobPost.setStatus(targetStatus);
            if ("open".equals(targetStatus)) {
                jobPost.setPostedAt(LocalDateTime.now());
            } else {
                jobPost.setPostedAt(null);
            }
        } else {
            jobPost.setStatus("draft");
            jobPost.setPostedAt(null);
        }

        try {
            JobPost savedPost = jobPostRepository.saveAndFlush(jobPost);
            List<JobAddressResponse> addresses = List.of();
            if (request.getAddresses() != null && !request.getAddresses().isEmpty()) {
                addresses = jobAddressService.syncAddresses(savedPost.getId(), request.getAddresses());
            }
            return JobPostResponse.fromEntity(savedPost, addresses);
        } catch (DataIntegrityViolationException ex) {
            throw conflict("Dữ liệu tin tuyển dụng vi phạm ràng buộc");
        }
    }

    @Transactional(readOnly = true)
    public Page<JobPostResponse> getAll(
            String keyword,
            String status,
            Long companyId,
            Long categoryId,
            Long provinceId,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String workingMode,
            List<Long> skillIds,
            Long specializationId,
            int page,
            int size,
            String sort) {
        validateStatusFilter(status);

        boolean isAdmin = currentUserService.isAdmin();
        var recruiterOpt = currentUserService.currentRecruiter();
        boolean isOwnerRecruiter = recruiterOpt.isPresent() && recruiterOpt.get().getCompanyId() != null
                && companyId != null && recruiterOpt.get().getCompanyId().equals(companyId);

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));

        if (!isAdmin && !isOwnerRecruiter) {
            // Đối với ứng viên, khách vãng lai hoặc nhà tuyển dụng xem tin công khai:
            // Chỉ hiển thị bài viết đã được duyệt (status = 'open')
            if (status != null && !"open".equalsIgnoreCase(status)) {
                return Page.empty(pageable);
            }
        }

        Specification<JobPost> spec = JobPostSpecification.filterJobs(
                keyword,
                status,
                companyId,
                categoryId,
                provinceId,
                salaryMin,
                salaryMax,
                workingMode,
                skillIds,
                specializationId,
                isAdmin,
                isOwnerRecruiter,
                sort
        );

        Page<JobPost> postsPage = jobPostRepository.findAll(spec, pageable);
        if (postsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<JobPost> posts = postsPage.getContent();
        List<Long> postIds = posts.stream().map(JobPost::getId).toList();

        List<JobAddress> allAddresses = jobAddressRepository.findByJobPost_IdIn(postIds);
        java.util.Map<Long, List<JobAddressResponse>> addressesByPostId = allAddresses.stream()
                .map(jobAddressService::toResponse)
                .collect(java.util.stream.Collectors.groupingBy(JobAddressResponse::getJobPostId));

        List<JobModeration> allModerations = jobModerationRepository.findByJobPost_IdInOrderByCreatedAtDescIdDesc(postIds);
        java.util.Map<Long, JobModerationResponse> latestModByPostId = new java.util.HashMap<>();
        for (JobModeration mod : allModerations) {
            if (mod.getJobPost() != null && !latestModByPostId.containsKey(mod.getJobPost().getId())) {
                latestModByPostId.put(mod.getJobPost().getId(), JobModerationResponse.fromEntity(mod));
            }
        }

        List<JobPostResponse> responses = posts.stream()
                .map(post -> {
                    JobPostResponse resp = JobPostResponse.fromEntity(
                            post,
                            addressesByPostId.getOrDefault(post.getId(), List.of()));
                    resp.setLatestModeration(latestModByPostId.get(post.getId()));
                    return resp;
                })
                .toList();

        return new PageImpl<>(responses, pageable, postsPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<JobPostResponse> getAll(
            String status,
            Long companyId,
            Long categoryId,
            Long provinceId) {
        return getAll(null, status, companyId, categoryId, provinceId, null, null, null, null, null, 0, 10, "newest");
    }

    @Transactional(readOnly = true)
    public List<JobPostResponse> getFeaturedJobs(Long categoryId) {
        List<JobPost> posts = jobPostRepository.findFeaturedJobs(categoryId);
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(JobPost::getId).toList();
        List<JobAddress> allAddresses = jobAddressRepository.findByJobPost_IdIn(postIds);
        java.util.Map<Long, List<JobAddressResponse>> addressesByPostId = allAddresses.stream()
                .map(jobAddressService::toResponse)
                .collect(java.util.stream.Collectors.groupingBy(JobAddressResponse::getJobPostId));

        List<JobModeration> allModerations = jobModerationRepository.findByJobPost_IdInOrderByCreatedAtDescIdDesc(postIds);
        java.util.Map<Long, JobModerationResponse> latestModByPostId = new java.util.HashMap<>();
        for (JobModeration mod : allModerations) {
            if (mod.getJobPost() != null && !latestModByPostId.containsKey(mod.getJobPost().getId())) {
                latestModByPostId.put(mod.getJobPost().getId(), JobModerationResponse.fromEntity(mod));
            }
        }

        return posts.stream()
                .map(post -> {
                    JobPostResponse resp = JobPostResponse.fromEntity(
                            post,
                            addressesByPostId.getOrDefault(post.getId(), List.of()));
                    resp.setLatestModeration(latestModByPostId.get(post.getId()));
                    return resp;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public JobPostResponse getById(Long id) {
        JobPost jobPost = findJobPost(id);

        if (!"open".equalsIgnoreCase(jobPost.getStatus())) {
            assertCanViewNonOpenJobPost(jobPost);
        }

        List<JobAddressResponse> addresses = jobAddressService.getByJobPostId(id);
        JobPostResponse response = JobPostResponse.fromEntity(jobPost, addresses);
        jobModerationRepository.findFirstByJobPost_IdOrderByCreatedAtDescIdDesc(id)
                .ifPresent(m -> response.setLatestModeration(JobModerationResponse.fromEntity(m)));
        return response;
    }

    @Transactional
    public JobPostResponse update(Long id, JobPostRequest request) {
        validateSalary(request.getSalaryMin(), request.getSalaryMax());

        JobPost jobPost = findJobPost(id);
        assertJobPostAccess(jobPost);

        JobsCategory category = jobsCategoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() -> notFound("Không tìm thấy danh mục"));

        jobPost.setCategory(category);
        applyRequest(jobPost, request);

        // Nhà tuyển dụng cập nhật tin không được tự ý kích hoạt 'open' (phải chờ admin duyệt lại)
        if (!currentUserService.isAdmin()) {
            if ("closed".equalsIgnoreCase(request.getStatus())) {
                jobPost.setStatus("closed");
            } else {
                jobPost.setStatus("draft");
                jobPost.setPostedAt(null);
            }
        } else {
            if ("open".equals(jobPost.getStatus()) && jobPost.getPostedAt() == null) {
                jobPost.setPostedAt(LocalDateTime.now());
            }
        }

        try {
            JobPost savedPost = jobPostRepository.saveAndFlush(jobPost);
            if (request.getAddresses() != null) {
                jobAddressService.syncAddresses(savedPost.getId(), request.getAddresses());
            }
            List<JobAddressResponse> addresses = jobAddressService.getByJobPostId(savedPost.getId());
            JobPostResponse response = JobPostResponse.fromEntity(savedPost, addresses);
            jobModerationRepository.findFirstByJobPost_IdOrderByCreatedAtDescIdDesc(savedPost.getId())
                    .ifPresent(m -> response.setLatestModeration(JobModerationResponse.fromEntity(m)));
            return response;
        } catch (DataIntegrityViolationException ex) {
            throw conflict("Dữ liệu tin tuyển dụng vi phạm ràng buộc");
        }
    }

    @Transactional
    public void delete(Long id) {
        JobPost jobPost = findJobPost(id);
        assertJobPostAccess(jobPost);

        try {
            jobAddressRepository.deleteByJobPost_Id(id);
            postSpecializationRepository.deleteByJobPost_Id(id);
            jobPostRepository.delete(jobPost);
            jobPostRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw conflict(
                    "Không thể xóa tin tuyển dụng vì đang có dữ liệu liên quan");
        }
    }

    @Transactional
    public JobPostResponse updateStatus(Long id, String status) {
        return updateStatus(id, status, null);
    }

    @Transactional
    public JobPostResponse updateStatus(Long id, String status, String reason) {
        validateStatusFilter(status);
        JobPost jobPost = findJobPost(id);
        assertJobPostAccess(jobPost);

        boolean isAdmin = currentUserService.isAdmin();

        // Chỉ Quản trị viên mới có quyền phê duyệt công khai tin tuyển dụng ('open')
        if ("open".equalsIgnoreCase(status) && !isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ Quản trị viên mới có quyền phê duyệt công khai tin tuyển dụng"
            );
        }

        jobPost.setStatus(status);
        if ("open".equals(status)) {
            if (jobPost.getPostedAt() == null) {
                jobPost.setPostedAt(LocalDateTime.now());
            }

            // Nếu Admin duyệt mở tin, cập nhật kết quả kiểm duyệt gần nhất thành 'approved'
            if (isAdmin) {
                Account adminAccount = currentUserService.currentAccount().orElse(null);
                jobModerationRepository.findFirstByJobPost_IdOrderByCreatedAtDescIdDesc(id)
                        .ifPresentOrElse(
                                mod -> {
                                    mod.setModerationStatus("approved");
                                    mod.setReviewedBy(adminAccount);
                                    mod.setReviewedAt(LocalDateTime.now());
                                    jobModerationRepository.save(mod);
                                },
                                () -> {
                                    JobModeration newMod = new JobModeration();
                                    newMod.setJobPost(jobPost);
                                    newMod.setModerationStatus("approved");
                                    newMod.setReviewedBy(adminAccount);
                                    newMod.setReviewedAt(LocalDateTime.now());
                                    newMod.setResult(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                                            .put("note", "Phê duyệt thủ công bởi Quản trị viên"));
                                    jobModerationRepository.save(newMod);
                                }
                        );

                // Gửi email thông báo phê duyệt công khai cho nhà tuyển dụng
                sendModerationEmail(jobPost, "approved", reason);
            }
        } else if ("closed".equals(status) && isAdmin) {
            // Nếu Admin từ chối hoặc đóng tin tuyển dụng
            Account adminAccount = currentUserService.currentAccount().orElse(null);
            jobModerationRepository.findFirstByJobPost_IdOrderByCreatedAtDescIdDesc(id)
                    .ifPresent(mod -> {
                        // Cho phép từ chối cả tin đang pending_review, error hoặc chưa duyệt
                        if (!"approved".equals(mod.getModerationStatus())) {
                            mod.setModerationStatus("rejected");
                            mod.setReviewedBy(adminAccount);
                            mod.setReviewedAt(LocalDateTime.now());
                            jobModerationRepository.save(mod);
                        }
                    });

            // Gửi email thông báo từ chối tin tuyển dụng cho nhà tuyển dụng
            sendModerationEmail(jobPost, "rejected", reason);
        }

        JobPost savedPost = jobPostRepository.saveAndFlush(jobPost);
        List<JobAddressResponse> addresses = jobAddressService.getByJobPostId(savedPost.getId());
        JobPostResponse response = JobPostResponse.fromEntity(savedPost, addresses);
        jobModerationRepository.findFirstByJobPost_IdOrderByCreatedAtDescIdDesc(savedPost.getId())
                .ifPresent(m -> response.setLatestModeration(JobModerationResponse.fromEntity(m)));
        return response;
    }

    private void sendModerationEmail(JobPost jobPost, String decision, String reason) {
        if (jobPost == null || jobPost.getPostedBy() == null) {
            return;
        }
        Recruiter recruiter = jobPost.getPostedBy();
        String recipientEmail = null;
        if (recruiter.getAccountId() != null) {
            recipientEmail = accountRepository.findById(recruiter.getAccountId())
                    .map(Account::getEmail)
                    .orElse(null);
        }

        if (recipientEmail != null && !recipientEmail.isBlank()) {
            String companyName = jobPost.getCompany() != null ? jobPost.getCompany().getName() : "";
            emailService.sendJobModerationResult(
                    recipientEmail,
                    recruiter.getFullName(),
                    jobPost.getTitle(),
                    companyName,
                    decision,
                    reason
            );
        }
    }

    public void assertJobPostAccess(JobPost jobPost) {
        if (currentUserService.isAdmin()) {
            return;
        }

        Recruiter recruiter = currentUserService.requireRecruiter();
        if (jobPost == null || jobPost.getCompany() == null || recruiter.getCompanyId() == null
                || !jobPost.getCompany().getId().equals(recruiter.getCompanyId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền quản lý tin tuyển dụng này"
            );
        }
    }

    private void assertCanViewNonOpenJobPost(JobPost jobPost) {
        if (currentUserService.isAdmin()) {
            return;
        }

        var recruiterOpt = currentUserService.currentRecruiter();
        if (recruiterOpt.isPresent()) {
            Recruiter currentRecruiter = recruiterOpt.get();
            if (currentRecruiter.getCompanyId() != null
                    && jobPost.getCompany() != null
                    && currentRecruiter.getCompanyId().equals(jobPost.getCompany().getId())) {
                return;
            }
        }

        throw notFound("Không tìm thấy tin tuyển dụng hoặc tin chưa được công khai");
    }

    private void applyRequest(JobPost jobPost, JobPostRequest request) {
        jobPost.setStatus(
                request.getStatus() == null ? "draft" : request.getStatus());
        jobPost.setTitle(request.getTitle().trim());
        jobPost.setEducationalRequirement(
                normalize(request.getEducationalRequirement()));
        if (request.getApplicationDeadline() != null && !request.getApplicationDeadline().equals(jobPost.getApplicationDeadline())) {
            jobPost.setExpirationReminderSent(false);
        }
        jobPost.setApplicationDeadline(request.getApplicationDeadline());
        jobPost.setVacancies(request.getVacancies());
        jobPost.setWorkingMode(request.getWorkingMode());
        jobPost.setSalaryMin(request.getSalaryMin());
        jobPost.setSalaryMax(request.getSalaryMax());
    }

    private void validateSalary(BigDecimal min, BigDecimal max) {
        if (min != null && min.signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lương tối thiểu không được âm");
        }

        if (max != null && max.signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lương tối đa không được âm");
        }

        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lương tối thiểu không được lớn hơn lương tối đa");
        }
    }

    private void validateStatusFilter(String status) {
        if (status != null
                && !List.of("draft", "open", "closed", "expired")
                        .contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trạng thái không hợp lệ");
        }
    }

    public JobPost findJobPost(Long id) {
        return jobPostRepository.findById(id)
                .orElseThrow(() -> notFound(
                        "Không tìm thấy tin tuyển dụng có ID: " + id));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    @Transactional(readOnly = true)
    public List<JobSpecializationResponse> getSpecializationsByJobPostId(Long jobPostId) {
        return postSpecializationRepository
                .findByJobPost_IdOrderBySpecialization_NameAsc(jobPostId)
                .stream()
                .map(jps -> {
                    JobSpecialization s = jps.getSpecialization();
                    return JobSpecializationResponse.builder()
                            .id(s.getId())
                            .jobCategoryId(s.getJobCategory().getId())
                            .jobCategoryName(s.getJobCategory().getName())
                            .name(s.getName())
                            .build();
                })
                .toList();
    }

    @Transactional
    public List<JobSpecializationResponse> updateJobPostSpecializations(
            Long jobPostId,
            List<Long> specializationIds
    ) {
        JobPost jobPost = findJobPost(jobPostId);
        assertJobPostAccess(jobPost);

        postSpecializationRepository.deleteByJobPost_Id(jobPostId);

        if (specializationIds != null && !specializationIds.isEmpty()) {
            for (Long specId : specializationIds) {
                if (specId == null) continue;
                JobSpecialization spec = jobSpecializationRepository.findById(specId)
                        .orElseThrow(() -> notFound("Không tìm thấy chuyên môn ID: " + specId));

                JobPostSpecialization jps = JobPostSpecialization.builder()
                        .jobPost(jobPost)
                        .specialization(spec)
                        .build();
                postSpecializationRepository.save(jps);
            }
        }

        return getSpecializationsByJobPostId(jobPostId);
    }

    @Transactional(readOnly = true)
    public List<JobAddressResponse> getAddressesByJobPostId(Long jobPostId) {
        return jobAddressService.getByJobPostId(jobPostId);
    }

    @Transactional
    public List<JobAddressResponse> updateJobPostAddresses(
            Long jobPostId,
            List<com.kiemjob.backend.dto.job.JobAddressRequest> requests
    ) {
        JobPost jobPost = findJobPost(jobPostId);
        assertJobPostAccess(jobPost);
        return jobAddressService.syncAddresses(jobPostId, requests);
    }
}
