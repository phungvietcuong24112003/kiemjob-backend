package com.kiemjob.backend.dto.profile;

import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.Recruiter;

public record RecruiterProfileResponse(
        Long id,
        Long accountId,
        String fullName,
        Long companyId,
        String companyName,
        String companyType,
        String logoUrl,
        String bannerUrl,
        String companyEmail,
        String companyPhone,
        String companyDescription,
        String status,
        String membershipTier,
        Long totalSpending
) {
    public RecruiterProfileResponse(
            Long id,
            Long accountId,
            String fullName,
            Long companyId,
            String companyName,
            String companyType,
            String logoUrl,
            String bannerUrl,
            String companyEmail,
            String companyPhone,
            String companyDescription,
            String status
    ) {
        this(
                id,
                accountId,
                fullName,
                companyId,
                companyName,
                companyType,
                logoUrl,
                bannerUrl,
                companyEmail,
                companyPhone,
                companyDescription,
                status,
                "bronze",
                0L
        );
    }

    public static RecruiterProfileResponse of(Recruiter recruiter, Company company) {
        return new RecruiterProfileResponse(
                recruiter != null ? recruiter.getId() : null,
                recruiter != null ? recruiter.getAccountId() : null,
                recruiter != null ? recruiter.getFullName() : null,
                company != null ? company.getId() : null,
                company != null ? company.getName() : null,
                company != null ? company.getCompanyType() : null,
                company != null ? company.getLogoUrl() : null,
                company != null ? company.getBannerUrl() : null,
                company != null ? company.getEmail() : null,
                company != null ? company.getPhone() : null,
                company != null ? company.getDescription() : null,
                company != null ? company.getStatus() : null,
                company != null && company.getMembershipTier() != null ? company.getMembershipTier() : "bronze",
                company != null && company.getTotalSpending() != null ? company.getTotalSpending() : 0L
        );
    }
}
