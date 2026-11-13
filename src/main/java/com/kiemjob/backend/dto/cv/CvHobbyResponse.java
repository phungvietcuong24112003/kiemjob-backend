package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.CvHobby;

public record CvHobbyResponse(
    Long id,
    Long cvId,
    String hobbyName
) {
    public static CvHobbyResponse from(CvHobby entity) {
        return new CvHobbyResponse(
            entity.getId(),
            entity.getCvId(),
            entity.getHobbyName()
        );
    }
}