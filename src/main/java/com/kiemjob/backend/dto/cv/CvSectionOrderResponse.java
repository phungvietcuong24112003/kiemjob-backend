package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.CvSectionOrder;

public record CvSectionOrderResponse(
    Long id,
    Long cvId,
    String sectionType,
    Integer displayOrder
) {
    public static CvSectionOrderResponse from(CvSectionOrder entity) {
        return new CvSectionOrderResponse(
            entity.getId(),
            entity.getCvId(),
            entity.getSectionType(),
            entity.getDisplayOrder()
        );
    }
}