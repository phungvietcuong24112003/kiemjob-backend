package com.kiemjob.backend.dto.application;

import com.kiemjob.backend.entity.Application;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.repository.AccountRepository;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ApplicationResponse {

    private Long id;
    private Long jobPostId;
    private String jobTitle;
    private Long companyId;
    private String companyName;
    private String companyLogo;
    private Long recruiterId;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String workingMode;
    private Long cvId;
    private String cvTitle;
    private Long candidateId;
    private String candidateName;
    private String candidateAvatar;
    private String candidateEmail;
    private String candidatePhone;
    private String candidateHeadline;
    private String candidateAddress;
    private String coverLatter;
    private String status;
    private LocalDateTime appliedAt;

    public static ApplicationResponse fromEntity(Application entity) {
        return fromEntity(entity, null);
    }

    public static ApplicationResponse fromEntity(Application entity, AccountRepository accountRepository) {
        ApplicationResponse response = new ApplicationResponse();

        response.setId(entity.getId());
        if (entity.getJobPost() != null) {
            response.setJobPostId(entity.getJobPost().getId());
            response.setJobTitle(entity.getJobPost().getTitle());
            response.setSalaryMin(entity.getJobPost().getSalaryMin());
            response.setSalaryMax(entity.getJobPost().getSalaryMax());
            response.setWorkingMode(entity.getJobPost().getWorkingMode());

            if (entity.getJobPost().getPostedBy() != null) {
                response.setRecruiterId(entity.getJobPost().getPostedBy().getId());
            }

            if (entity.getJobPost().getCompany() != null) {
                response.setCompanyId(entity.getJobPost().getCompany().getId());
                response.setCompanyName(entity.getJobPost().getCompany().getName());
                response.setCompanyLogo(entity.getJobPost().getCompany().getLogoUrl());
            }
        }

        if (entity.getCv() != null) {
            response.setCvId(entity.getCv().getId());
            response.setCvTitle(entity.getCv().getTitle());
            if (entity.getCv().getCandidate() != null) {
                Candidate candidate = entity.getCv().getCandidate();
                response.setCandidateId(candidate.getId());
                response.setCandidateName(candidate.getFullname());
                response.setCandidateAvatar(candidate.getAvatarUrl());
                response.setCandidatePhone(candidate.getPhone());
                response.setCandidateHeadline(candidate.getHeadline());
                response.setCandidateAddress(candidate.getAddressDetail());

                if (candidate.getAccountId() != null && accountRepository != null) {
                    accountRepository.findById(candidate.getAccountId())
                            .ifPresent(account -> response.setCandidateEmail(account.getEmail()));
                }
            }
        }

        response.setCoverLatter(entity.getCoverLatter());
        response.setStatus(entity.getStatus());
        response.setAppliedAt(entity.getAppliedAt());

        return response;
    }
}
