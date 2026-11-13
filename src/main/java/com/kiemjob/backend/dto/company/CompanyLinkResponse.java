
package com.kiemjob.backend.dto.company;

public record CompanyLinkResponse(
    Long id,
    Long companyId,
    String type,
    String link
) {}
