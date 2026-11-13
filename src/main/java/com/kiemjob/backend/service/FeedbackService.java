package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.feedback.FeedbackCreateRequest;
import com.kiemjob.backend.dto.feedback.FeedbackResponse;
import com.kiemjob.backend.dto.feedback.FeedbackStatsResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.Feedback;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.CandidateRepository;
import com.kiemjob.backend.repository.CompanyRepository;
import com.kiemjob.backend.repository.FeedbackRepository;
import com.kiemjob.backend.repository.RecruiterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final AccountRepository accountRepository;
    private final CandidateRepository candidateRepository;
    private final RecruiterRepository recruiterRepository;
    private final CompanyRepository companyRepository;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            AccountRepository accountRepository,
            CandidateRepository candidateRepository,
            RecruiterRepository recruiterRepository,
            CompanyRepository companyRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.accountRepository = accountRepository;
        this.candidateRepository = candidateRepository;
        this.recruiterRepository = recruiterRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional
    public FeedbackResponse createFeedback(String authEmail, FeedbackCreateRequest request) {
        Account account = null;

        if (authEmail != null && !authEmail.isBlank() && !"anonymousUser".equalsIgnoreCase(authEmail)) {
            account = accountRepository.findByEmailIgnoreCase(authEmail.trim()).orElse(null);
        }

        if (account == null && request.email() != null && !request.email().isBlank()) {
            account = accountRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        }

        if (account == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập tài khoản để gửi góp ý phản hồi.");
        }

        String resolvedSubject = request.getResolvedSubject();
        String resolvedContent = request.getResolvedContent();

        if (resolvedSubject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chủ đề góp ý không được để trống.");
        }

        if (resolvedContent.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung góp ý không được để trống.");
        }

        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đánh giá sao phải từ 1 đến 5 sao.");
        }

        Feedback feedback = new Feedback();
        feedback.setAccountId(account.getId());
        feedback.setSubject(resolvedSubject);
        feedback.setRating(request.rating());
        feedback.setContent(resolvedContent);
        feedback.setCreatedAt(LocalDateTime.now());

        Feedback saved = feedbackRepository.save(feedback);
        return mapToResponse(saved, account);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getAllFeedbacks() {
        List<Feedback> list = feedbackRepository.findAllByOrderByCreatedAtDesc();
        List<FeedbackResponse> responses = new ArrayList<>();
        Map<Long, Account> accountCache = new HashMap<>();

        for (Feedback fb : list) {
            Account acc = accountCache.computeIfAbsent(fb.getAccountId(), id -> accountRepository.findById(id).orElse(null));
            responses.add(mapToResponse(fb, acc));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getMyFeedbacks(String authEmail) {
        if (authEmail == null || authEmail.isBlank() || "anonymousUser".equalsIgnoreCase(authEmail)) {
            return Collections.emptyList();
        }
        Account account = accountRepository.findByEmailIgnoreCase(authEmail.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại."));

        List<Feedback> list = feedbackRepository.findByAccountIdOrderByCreatedAtDesc(account.getId());
        List<FeedbackResponse> responses = new ArrayList<>();
        for (Feedback fb : list) {
            responses.add(mapToResponse(fb, account));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public FeedbackStatsResponse getStats() {
        List<Feedback> list = feedbackRepository.findAll();
        long total = list.size();
        if (total == 0) {
            return new FeedbackStatsResponse(0, 0.0, 0, 0, Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L));
        }

        double sumRating = 0;
        Map<Integer, Long> counts = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            counts.put(i, 0L);
        }

        long candidateCount = 0;
        long recruiterCount = 0;

        for (Feedback fb : list) {
            sumRating += fb.getRating();
            counts.put(fb.getRating(), counts.getOrDefault(fb.getRating(), 0L) + 1);

            Account acc = accountRepository.findById(fb.getAccountId()).orElse(null);
            if (acc != null) {
                if ("candidate".equalsIgnoreCase(acc.getRole())) {
                    candidateCount++;
                } else if ("recruiter".equalsIgnoreCase(acc.getRole())) {
                    recruiterCount++;
                }
            }
        }

        double avg = Math.round((sumRating / total) * 10.0) / 10.0;
        return new FeedbackStatsResponse(total, avg, candidateCount, recruiterCount, counts);
    }

    @Transactional
    public void deleteFeedback(Long id) {
        if (!feedbackRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy góp ý ID: " + id);
        }
        feedbackRepository.deleteById(id);
    }

    private FeedbackResponse mapToResponse(Feedback fb, Account account) {
        String email = account != null ? account.getEmail() : "Không xác định";
        String role = account != null ? account.getRole() : "unknown";
        String senderName = email;

        if (account != null) {
            if ("candidate".equalsIgnoreCase(account.getRole())) {
                Candidate cand = candidateRepository.findByAccountId(account.getId()).orElse(null);
                if (cand != null && cand.getFullname() != null && !cand.getFullname().isBlank()) {
                    senderName = cand.getFullname();
                }
            } else if ("recruiter".equalsIgnoreCase(account.getRole())) {
                Recruiter rec = recruiterRepository.findByAccountId(account.getId()).orElse(null);
                if (rec != null) {
                    StringBuilder nameBuilder = new StringBuilder();
                    if (rec.getFullName() != null && !rec.getFullName().isBlank()) {
                        nameBuilder.append(rec.getFullName());
                    }
                    if (rec.getCompanyId() != null) {
                        Company comp = companyRepository.findById(rec.getCompanyId()).orElse(null);
                        if (comp != null && comp.getName() != null && !comp.getName().isBlank()) {
                            if (nameBuilder.length() > 0) {
                                nameBuilder.append(" - ").append(comp.getName());
                            } else {
                                nameBuilder.append(comp.getName());
                            }
                        }
                    }
                    if (nameBuilder.length() > 0) {
                        senderName = nameBuilder.toString();
                    }
                }
            } else if ("admin".equalsIgnoreCase(account.getRole())) {
                senderName = "Quản trị viên (" + email + ")";
            }
        }

        return new FeedbackResponse(
                fb.getId(),
                fb.getAccountId(),
                email,
                role,
                senderName,
                fb.getSubject(),
                fb.getRating(),
                fb.getContent(),
                fb.getCreatedAt()
        );
    }
}
