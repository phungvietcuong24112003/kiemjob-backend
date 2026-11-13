package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.CvCertificate;

import java.time.LocalDate;

public record CvCertificateResponse(
    Long id,
    Long cvId,
    LocalDate certificateDate,
    String certificateName,
    String description
) {
    public static CvCertificateResponse from(CvCertificate entity) {
        return new CvCertificateResponse(
            entity.getId(),
            entity.getCvId(),
            entity.getCertificateDate(),
            entity.getCertificateName(),
            entity.getDescription()
        );
    }
}