package com.kiemjob.backend.dto.company;

public record CompanyLicenseResponse(
        Long id,
        Long companyId,
        String fileUrl
) {
}
