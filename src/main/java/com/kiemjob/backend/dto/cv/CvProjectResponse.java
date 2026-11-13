package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.CvProject;

import java.time.LocalDate;

public record CvProjectResponse(
    Long id,
    Long cvId,
    String projectName,
    String role,
    LocalDate startDate,
    LocalDate endDate,
    Boolean isCurrent,
    String description,
    String technologies,
    String projectUrl,
    String repositoryUrl
) {
    public static CvProjectResponse from(CvProject entity) {
        return new CvProjectResponse(
            entity.getId(),
            entity.getCvId(),
            entity.getProjectName(),
            entity.getRole(),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.getIsCurrent(),
            entity.getDescription(),
            entity.getTechnologies(),
            entity.getProjectUrl(),
            entity.getRepositoryUrl()
        );
    }
}