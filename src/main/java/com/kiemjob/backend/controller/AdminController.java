package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.feedback.FeedbackResponse;
import com.kiemjob.backend.dto.feedback.FeedbackStatsResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.*;
import com.kiemjob.backend.service.FeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final CompanyRepository companyRepository;
    private final JobPostRepository jobPostRepository;
    private final AccountRepository accountRepository;
    private final CandidateRepository candidateRepository;
    private final RecruiterRepository recruiterRepository;
    private final FeedbackService feedbackService;

    public AdminController(
            CompanyRepository companyRepository,
            JobPostRepository jobPostRepository,
            AccountRepository accountRepository,
            CandidateRepository candidateRepository,
            RecruiterRepository recruiterRepository,
            FeedbackService feedbackService
    ) {
        super();
        this.companyRepository = companyRepository;
        this.jobPostRepository = jobPostRepository;
        this.accountRepository = accountRepository;
        this.candidateRepository = candidateRepository;
        this.recruiterRepository = recruiterRepository;
        this.feedbackService = feedbackService;
    }

    public record AdminStatsResponse(
            long totalCompanies,
            long pendingCompanies,
            long approvedCompanies,
            long totalJobPosts,
            long draftJobPosts,
            long openJobPosts,
            long totalAccounts,
            long totalCandidates,
            long totalRecruiters
    ) {}

    public record AdminAccountResponse(
            Long id,
            String email,
            String name,
            String role,
            boolean emailVerified,
            String status
    ) {}

    @GetMapping("/stats")
    public AdminStatsResponse getStats() {
        long totalCompanies = companyRepository.count();
        long pendingCompanies = companyRepository.countByStatus("pending");
        long approvedCompanies = companyRepository.countByStatus("approved");

        long totalJobPosts = jobPostRepository.count();
        long draftJobPosts = jobPostRepository.search("draft", null, null).size();
        long openJobPosts = jobPostRepository.search("open", null, null).size();

        long totalAccounts = accountRepository.count();
        long totalCandidates = candidateRepository.count();
        long totalRecruiters = recruiterRepository.count();

        return new AdminStatsResponse(
                totalCompanies,
                pendingCompanies,
                approvedCompanies,
                totalJobPosts,
                draftJobPosts,
                openJobPosts,
                totalAccounts,
                totalCandidates,
                totalRecruiters
        );
    }

    @GetMapping("/accounts")
    public List<AdminAccountResponse> getAccounts() {
        List<Account> accounts = accountRepository.findAll();
        List<AdminAccountResponse> result = new ArrayList<>();

        for (Account acc : accounts) {
            String name = acc.getEmail();
            if ("candidate".equalsIgnoreCase(acc.getRole())) {
                Candidate c = candidateRepository.findByAccountId(acc.getId()).orElse(null);
                if (c != null && c.getFullname() != null && !c.getFullname().isBlank()) {
                    name = c.getFullname();
                }
            } else if ("recruiter".equalsIgnoreCase(acc.getRole())) {
                Recruiter r = recruiterRepository.findByAccountId(acc.getId()).orElse(null);
                if (r != null && r.getFullName() != null && !r.getFullName().isBlank()) {
                    name = r.getFullName();
                }
            } else if ("admin".equalsIgnoreCase(acc.getRole())) {
                name = "Quản trị viên (" + acc.getEmail() + ")";
            }

            String status = acc.isEmailVerified() ? "Hoạt động" : "Chờ xác thực";
            result.add(new AdminAccountResponse(
                    acc.getId(),
                    acc.getEmail(),
                    name,
                    acc.getRole(),
                    acc.isEmailVerified(),
                    status
            ));
        }

        return result;
    }

    @PatchMapping("/accounts/{id}/toggle-verify")
    public AdminAccountResponse toggleAccountVerify(@PathVariable Long id) {
        Account acc = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));

        acc.setEmailVerified(!acc.isEmailVerified());
        accountRepository.save(acc);

        String name = acc.getEmail();
        if ("candidate".equalsIgnoreCase(acc.getRole())) {
            Candidate c = candidateRepository.findByAccountId(acc.getId()).orElse(null);
            if (c != null && c.getFullname() != null) name = c.getFullname();
        } else if ("recruiter".equalsIgnoreCase(acc.getRole())) {
            Recruiter r = recruiterRepository.findByAccountId(acc.getId()).orElse(null);
            if (r != null && r.getFullName() != null) name = r.getFullName();
        }

        String status = acc.isEmailVerified() ? "Hoạt động" : "Chờ xác thực";
        return new AdminAccountResponse(
                acc.getId(),
                acc.getEmail(),
                name,
                acc.getRole(),
                acc.isEmailVerified(),
                status
        );
    }

    // ==================== QUẢN LÝ GÓP Ý & PHẢN HỒI ====================

    @GetMapping("/feedbacks")
    public List<FeedbackResponse> getFeedbacks() {
        return feedbackService.getAllFeedbacks();
    }

    @GetMapping("/feedbacks/stats")
    public FeedbackStatsResponse getFeedbackStats() {
        return feedbackService.getStats();
    }

    @DeleteMapping("/feedbacks/{id}")
    public ResponseEntity<?> deleteFeedback(@PathVariable Long id) {
        feedbackService.deleteFeedback(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã xóa góp ý thành công."
        ));
    }
}
