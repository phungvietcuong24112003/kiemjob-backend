
package com.kiemjob.backend.dto.company;

public record CompanyAddressResponse(
    Long id,
    Long companyId,
    Long provinceId,
    String provinceName,
    String addressDetail
) {}
