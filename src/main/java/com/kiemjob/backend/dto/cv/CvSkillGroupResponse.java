package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.CvSkillGroup;

public record CvSkillGroupResponse(
    Long id,
    Long cvId,
    String title,
    String content,
    Integer displayOrder
) {
    public static CvSkillGroupResponse from(CvSkillGroup entity) {
        return new CvSkillGroupResponse(
            entity.getId(),
            entity.getCvId(),
            entity.getTitle(),
            entity.getContent(),
            entity.getDisplayOrder()
        );
    }
}