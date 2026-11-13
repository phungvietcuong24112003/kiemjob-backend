package com.kiemjob.backend.security;

import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.CandidateRepository;
import com.kiemjob.backend.repository.RecruiterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AccountRepository accountRepository;
    private final CandidateRepository candidateRepository;
    private final RecruiterRepository recruiterRepository;

    public Optional<Authentication> getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return Optional.empty();
        }
        return Optional.of(auth);
    }

    public Optional<Account> currentAccount() {
        return getAuthentication()
                .flatMap(auth -> accountRepository.findByEmailIgnoreCase(auth.getName()));
    }

    public Account requireAccount() {
        return currentAccount()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Vui lòng đăng nhập để thực hiện thao tác này"
                ));
    }

    public Optional<Candidate> currentCandidate() {
        return currentAccount()
                .flatMap(acc -> candidateRepository.findByAccountId(acc.getId()));
    }

    public Candidate requireCandidate() {
        Account account = requireAccount();
        if (!"candidate".equalsIgnoreCase(account.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ tài khoản ứng viên mới có thể thực hiện thao tác này"
            );
        }
        return candidateRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Hồ sơ ứng viên chưa được khởi tạo"
                ));
    }

    public Optional<Recruiter> currentRecruiter() {
        return currentAccount()
                .flatMap(acc -> recruiterRepository.findByAccountId(acc.getId()));
    }

    public Recruiter requireRecruiter() {
        Account account = requireAccount();
        if (!"recruiter".equalsIgnoreCase(account.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ tài khoản nhà tuyển dụng mới có thể thực hiện thao tác này"
            );
        }
        Recruiter recruiter = recruiterRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản nhà tuyển dụng chưa hoàn tất hồ sơ"
                ));
        if (recruiter.getCompanyId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản nhà tuyển dụng chưa liên kết với công ty"
            );
        }
        return recruiter;
    }

    public boolean isAdmin() {
        return getAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority())))
                .orElse(false);
    }

    public boolean isCandidate() {
        return getAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_CANDIDATE".equalsIgnoreCase(a.getAuthority())))
                .orElse(false);
    }

    public boolean isRecruiter() {
        return getAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_RECRUITER".equalsIgnoreCase(a.getAuthority())))
                .orElse(false);
    }

    public void assertCanManageCompany(Long companyId) {
        if (isAdmin()) {
            return;
        }
        Recruiter recruiter = requireRecruiter();
        if (companyId == null || recruiter.getCompanyId() == null || !recruiter.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền quản lý công ty này"
            );
        }
    }

    public void assertCanManageJobPost(JobPost jobPost) {
        if (isAdmin()) {
            return;
        }
        Recruiter recruiter = requireRecruiter();
        boolean sameCompany = jobPost != null && jobPost.getCompany() != null
                && recruiter.getCompanyId() != null
                && jobPost.getCompany().getId().equals(recruiter.getCompanyId());
        boolean samePostedBy = jobPost != null && jobPost.getPostedBy() != null
                && recruiter.getId() != null
                && recruiter.getId().equals(jobPost.getPostedBy().getId());

        if (!sameCompany && !samePostedBy) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền quản lý tin tuyển dụng này"
            );
        }
    }
}

