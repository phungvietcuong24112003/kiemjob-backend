package com.kiemjob.backend.dto.company;

import com.kiemjob.backend.entity.Company;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyResponse {

    private Long id;
    private String companyType;
    private String name;
    private String logoUrl;
    private String bannerUrl;
    private String email;
    private String phone;
    private String description;
    private String status;
    private String membershipTier;
    private Long totalSpending;

    public String getSubscriptionType() {
        return membershipTier;
    }

    public static CompanyResponse fromEntity(Company company) {
        CompanyResponse response = new CompanyResponse();

        response.setId(company.getId());
        response.setCompanyType(company.getCompanyType());
        response.setName(company.getName());
        response.setLogoUrl(company.getLogoUrl());
        response.setBannerUrl(company.getBannerUrl());
        response.setEmail(company.getEmail());
        response.setPhone(company.getPhone());
        response.setDescription(company.getDescription());
        response.setStatus(company.getStatus());
        response.setMembershipTier(company.getMembershipTier());
        response.setTotalSpending(company.getTotalSpending() != null ? company.getTotalSpending() : 0L);

        return response;
    }
}
