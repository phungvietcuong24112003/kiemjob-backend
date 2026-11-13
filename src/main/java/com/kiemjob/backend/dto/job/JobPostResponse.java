package com.kiemjob.backend.dto.job;

import com.kiemjob.backend.dto.moderation.JobModerationResponse;
import com.kiemjob.backend.entity.JobPost;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class JobPostResponse {

    private Long id;

    private Long companyId;
    private String companyName;
    private String companyLogo;

    private Long categoryId;
    private String categoryName;

    private Long postedById;

    private String status;
    private String title;
    private String educationalRequirement;
    private LocalDate applicationDeadline;
    private Integer vacancies;
    private String workingMode;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private LocalDateTime postedAt;

    private Boolean isFeatured = false;
    private LocalDateTime featuredUntil;

    private java.util.List<JobAddressResponse> addresses = new java.util.ArrayList<>();
    private String location;

    private JobModerationResponse latestModeration;

    public static JobPostResponse fromEntity(JobPost entity) {
        return fromEntity(entity, java.util.List.of());
    }

    public static JobPostResponse fromEntity(JobPost entity, java.util.List<JobAddressResponse> addresses) {
        JobPostResponse response = new JobPostResponse();

        response.setId(entity.getId());

        if (entity.getCompany() != null) {
            response.setCompanyId(entity.getCompany().getId());
            response.setCompanyName(entity.getCompany().getName());
            response.setCompanyLogo(entity.getCompany().getLogoUrl());
        }

        if (entity.getCategory() != null) {
            response.setCategoryId(entity.getCategory().getId());
            response.setCategoryName(entity.getCategory().getName());
        }

        if (entity.getPostedBy() != null) {
            response.setPostedById(entity.getPostedBy().getId());
        }

        response.setStatus(entity.getStatus());
        response.setTitle(entity.getTitle());
        response.setEducationalRequirement(
                entity.getEducationalRequirement());
        response.setApplicationDeadline(entity.getApplicationDeadline());
        response.setVacancies(entity.getVacancies());
        response.setWorkingMode(entity.getWorkingMode());
        response.setSalaryMin(entity.getSalaryMin());
        response.setSalaryMax(entity.getSalaryMax());
        response.setPostedAt(entity.getPostedAt());

        // Kiểm tra xem tin có đang trong thời hạn nổi bật hay không
        boolean activeFeatured = Boolean.TRUE.equals(entity.getIsFeatured())
                && (entity.getFeaturedUntil() == null || entity.getFeaturedUntil().isAfter(LocalDateTime.now()));
        response.setIsFeatured(activeFeatured);
        response.setFeaturedUntil(entity.getFeaturedUntil());

        if (addresses != null) {
            response.setAddresses(addresses);
            java.util.List<String> provinces = addresses.stream()
                    .map(JobAddressResponse::getProvinceName)
                    .filter(p -> p != null && !p.isBlank())
                    .distinct()
                    .toList();
            if (!provinces.isEmpty()) {
                response.setLocation(String.join(", ", provinces));
            } else if (!addresses.isEmpty()) {
                response.setLocation(addresses.get(0).getAddressDetail());
            } else {
                response.setLocation("Chưa cập nhật địa điểm");
            }
        } else {
            response.setLocation("Chưa cập nhật địa điểm");
        }

        return response;
    }
}
