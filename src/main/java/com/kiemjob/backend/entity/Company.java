package com.kiemjob.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "companies",
    indexes = {
        @Index(name = "idx_companies_name", columnList = "name"),
        @Index(name = "idx_companies_status", columnList = "status"),
        @Index(name = "idx_companies_membership_tier", columnList = "membership_tier")
    }
)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_type", nullable = false, length = 10)
    private String companyType;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "membership_tier", nullable = false, length = 20)
    private String membershipTier = "bronze";

    @Column(name = "total_spending", nullable = false)
    private Long totalSpending = 0L;

    public Company() {
    }

    public Long getId() {
        return id;
    }

    public String getCompanyType() {
        return companyType;
    }

    public void setCompanyType(String companyType) {
        this.companyType = companyType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMembershipTier() {
        return membershipTier;
    }

    public void setMembershipTier(String membershipTier) {
        this.membershipTier = membershipTier;
    }

    public Long getTotalSpending() {
        return totalSpending;
    }

    public void setTotalSpending(Long totalSpending) {
        this.totalSpending = totalSpending != null ? totalSpending : 0L;
        this.membershipTier = calculateMembershipTier(this.totalSpending);
    }

    /**
     * Tính toán xếp hạng thành viên dựa vào tổng số tiền chi tiêu:
     * - Dưới 500.000 VNĐ: bronze (mặc định)
     * - Từ 500.000 VNĐ đến dưới 2.000.000 VNĐ: silver
     * - Từ 2.000.000 VNĐ đến dưới 5.000.000 VNĐ: gold
     * - Từ 5.000.000 VNĐ trở lên: platinum
     */
    public static String calculateMembershipTier(Long spending) {
        if (spending == null || spending < 500_000L) {
            return "bronze";
        } else if (spending < 2_000_000L) {
            return "silver";
        } else if (spending < 5_000_000L) {
            return "gold";
        } else {
            return "platinum";
        }
    }

    /**
     * Tích lũy số tiền thanh toán vào tổng chi tiêu và tự động cập nhật hạng thành viên
     */
    public void addSpending(Long amount) {
        if (amount != null && amount > 0) {
            long current = this.totalSpending != null ? this.totalSpending : 0L;
            this.totalSpending = current + amount;
            this.membershipTier = calculateMembershipTier(this.totalSpending);
        }
    }

    @Deprecated
    public String getSubscriptionType() {
        return membershipTier;
    }

    @Deprecated
    public void setSubscriptionType(String subscriptionType) {
        if (subscriptionType != null) {
            this.membershipTier = subscriptionType;
        }
    }
}