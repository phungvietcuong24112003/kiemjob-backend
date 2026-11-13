package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.CvAward;

import java.time.LocalDate;

public record CvAwardResponse(
    Long id,
    Long cvId,
    LocalDate awardDate,
    String awardName,
    String description
) {
    public static CvAwardResponse from(CvAward entity) {
        return new CvAwardResponse(
            entity.getId(),
            entity.getCvId(),
            entity.getAwardDate(),
            entity.getAwardName(),
            entity.getDescription()
        );
    }
}