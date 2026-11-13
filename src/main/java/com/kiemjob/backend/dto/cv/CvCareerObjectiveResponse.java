package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.CvCareerObjective;

public record CvCareerObjectiveResponse(
    Long id,
    Long cvId,
    String content
) {
    public static CvCareerObjectiveResponse from(
            CvCareerObjective entity) {

        return new CvCareerObjectiveResponse(
            entity.getId(),
            entity.getCvId(),
            entity.getContent()
        );
    }
}