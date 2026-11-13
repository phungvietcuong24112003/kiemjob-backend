package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.auth.AccountResponse;
import com.kiemjob.backend.dto.profile.*;
import com.kiemjob.backend.entity.*;
import com.kiemjob.backend.repository.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final AccountRepository accountRepository;
    private final CandidateRepository candidateRepository;
    private final RecruiterRepository recruiterRepository;
    private final CompanyRepository companyRepository;
    private final ProvinceRepository provinceRepository;

    public ProfileService(
            AccountRepository accountRepository,
            CandidateRepository candidateRepository,
            RecruiterRepository recruiterRepository,
            CompanyRepository companyRepository,
            ProvinceRepository provinceRepository
    ) {
        this.accountRepository = accountRepository;
        this.candidateRepository = candidateRepository;
        this.recruiterRepository = recruiterRepository;
        this.companyRepository = companyRepository;
        this.provinceRepository = provinceRepository;
    }

    // ────────────────────────────────────────────────────────────
    //  HỒ SƠ ỨNG VIÊN (LẤY VÀ BỔ SUNG/CẬP NHẬT)
    // ────────────────────────────────────────────────────────────

    @Transactional
    public CandidateProfileResponse getCandidateProfile(String email) {
        Account account = getAccountByEmail(email);
        assertRole(account, "candidate", "Tài khoản không phải là ứng viên");

        Candidate candidate = candidateRepository.findByAccountId(account.getId())
                .orElseGet(() -> {
                    Candidate c = new Candidate();
                    c.setAccountId(account.getId());
                    c.setFullname(deriveCandidateName(account.getEmail()));
                    return candidateRepository.save(c);
                });

        String provinceName = getProvinceName(candidate.getProvinceId());
        return CandidateProfileResponse.from(candidate, provinceName, account.getEmail());
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getCandidateProfileById(Long id) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ ứng viên"));
        String provinceName = getProvinceName(candidate.getProvinceId());
        String email = null;
        if (candidate.getAccountId() != null) {
            email = accountRepository.findById(candidate.getAccountId()).map(Account::getEmail).orElse(null);
        }
        return CandidateProfileResponse.from(candidate, provinceName, email);
    }

    @Transactional
    public CandidateProfileResponse updateCandidateProfile(String email, CandidateProfileRequest request) {
        Account account = getAccountByEmail(email);
        assertRole(account, "candidate", "Tài khoản không phải là ứng viên");

        if (request.provinceId() != null && !provinceRepository.existsById(request.provinceId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tỉnh/thành phố không tồn tại"
            );
        }

        Candidate candidate = candidateRepository.findByAccountId(account.getId())
                .orElseGet(() -> {
                    Candidate c = new Candidate();
                    c.setAccountId(account.getId());
                    return c;
                });

        candidate.setFullname(request.fullname().trim());
        candidate.setAvatarUrl(request.avatarUrl());
        candidate.setHeadline(request.headline());
        candidate.setGender(request.gender() == null || request.gender().isBlank() ? null : request.gender());
        candidate.setPhone(request.phone());
        candidate.setDateOfBirth(request.dateOfBirth());
        candidate.setProvinceId(request.provinceId());
        candidate.setAddressDetail(request.addressDetail());

        candidate = candidateRepository.save(candidate);

        String provinceName = getProvinceName(candidate.getProvinceId());
        return CandidateProfileResponse.from(candidate, provinceName);
    }

    // ────────────────────────────────────────────────────────────
    // ── HỒ SƠ NHÀ TUYỂN DỤNG & CÔNG TY (LẤY VÀ BỔ SUNG/CẬP NHẬT) ─
    @Transactional(readOnly = true)
    public RecruiterProfileResponse getRecruiterProfile(String email) {
        Account account = getAccountByEmail(email);
        assertRole(account, "recruiter", "Tài khoản không phải là nhà tuyển dụng");

        var recruiterOpt = recruiterRepository.findByAccountId(account.getId());
        if (recruiterOpt.isEmpty()) {
            // Khi đăng ký xong tài khoản cho recruiter thì các thông tin liên quan đến companies là NULL
            return new RecruiterProfileResponse(
                    null,
                    account.getId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        Recruiter recruiter = recruiterOpt.get();
        Company company = recruiter.getCompanyId() != null
                ? companyRepository.findById(recruiter.getCompanyId()).orElse(null)
                : null;

        return RecruiterProfileResponse.of(recruiter, company);
    }

    @Transactional
    public RecruiterProfileResponse updateRecruiterProfile(String email, RecruiterProfileRequest request) {
        Account account = getAccountByEmail(email);
        assertRole(account, "recruiter", "Chỉ tài khoản nhà tuyển dụng mới có thể cập nhật hồ sơ");

        Recruiter recruiter = recruiterRepository.findByAccountId(account.getId())
                .orElse(null);

        Company company;
        if (recruiter != null && recruiter.getCompanyId() != null) {
            company = companyRepository.findById(recruiter.getCompanyId())
                    .orElseGet(Company::new);
        } else {
            if (recruiter == null) {
                recruiter = new Recruiter();
                recruiter.setAccountId(account.getId());
            }
            company = new Company();
        }

        company.setName(request.companyName().trim());
        company.setCompanyType(
                request.companyType() != null && !request.companyType().isBlank()
                        ? request.companyType()
                        : "small"
        );
        company.setLogoUrl(request.logoUrl());
        company.setBannerUrl(request.bannerUrl());
        company.setEmail(request.companyEmail());
        company.setPhone(request.companyPhone());
        company.setDescription(request.companyDescription());
        // Sau khi điền thông tin xong thì trong trạng thái chờ duyệt (pending)
        company.setStatus("pending");

        company = companyRepository.saveAndFlush(company);

        recruiter.setCompanyId(company.getId());
        recruiter.setFullName(request.fullName().trim());

        recruiter = recruiterRepository.save(recruiter);

        return RecruiterProfileResponse.of(recruiter, company);
    }

    // ────────────────────────────────────────────────────────────
    //  LẤY TOÀN BỘ HỒ SƠ CỦA NGƯỜI DÙNG HIỆN TẠI
    // ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(String email) {
        Account account = getAccountByEmail(email);
        boolean completed = false;
        Object profileData = null;

        if ("candidate".equalsIgnoreCase(account.getRole())) {
            var candidateOpt = candidateRepository.findByAccountId(account.getId());
            if (candidateOpt.isPresent()) {
                completed = true;
                Candidate c = candidateOpt.get();
                profileData = CandidateProfileResponse.from(c, getProvinceName(c.getProvinceId()));
            }
        } else if ("recruiter".equalsIgnoreCase(account.getRole())) {
            var recruiterOpt = recruiterRepository.findByAccountId(account.getId());
            if (recruiterOpt.isPresent()) {
                Recruiter r = recruiterOpt.get();
                if (r.getCompanyId() != null) {
                    var companyOpt = companyRepository.findById(r.getCompanyId());
                    if (companyOpt.isPresent()) {
                        completed = true;
                        profileData = RecruiterProfileResponse.of(r, companyOpt.get());
                    } else {
                        profileData = RecruiterProfileResponse.of(r, null);
                    }
                } else {
                    profileData = RecruiterProfileResponse.of(r, null);
                }
            } else {
                completed = false;
                profileData = new RecruiterProfileResponse(
                        null,
                        account.getId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }
        } else {
            completed = true;
        }

        return new MyProfileResponse(
                AccountResponse.of(account, completed),
                completed,
                profileData
        );
    }

    // ────────────────────────────────────────────────────────────
    //  HELPERS
    // ────────────────────────────────────────────────────────────

    private Account getAccountByEmail(String email) {
        return accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Tài khoản không tồn tại"
                ));
    }

    private void assertRole(Account account, String expectedRole, String errorMessage) {
        if (!expectedRole.equalsIgnoreCase(account.getRole())
                && !"admin".equalsIgnoreCase(account.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, errorMessage);
        }
    }

    private String getProvinceName(Long provinceId) {
        if (provinceId == null) {
            return null;
        }
        return provinceRepository.findById(provinceId)
                .map(Province::getName)
                .orElse(null);
    }

    private String deriveCandidateName(String email) {
        if (email == null || email.isBlank()) {
            return "user";
        }
        int atIndex = email.indexOf('@');
        String name;
        if (atIndex > 0) {
            name = email.substring(0, atIndex).trim();
        } else if (atIndex == 0) {
            name = "user";
        } else {
            name = email.trim();
        }
        if (name.isBlank()) {
            return "user";
        }
        if (name.length() > 150) {
            name = name.substring(0, 150);
        }
        return name;
    }
}
