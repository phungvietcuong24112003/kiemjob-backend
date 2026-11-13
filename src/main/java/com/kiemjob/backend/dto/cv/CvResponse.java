package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.Cv;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CvResponse {

    private Long id;
    private Long candidateId;
    private String title;
    private Boolean isPublic;
    private Boolean hasPdf;
    private Boolean isWebCreated;

    public static CvResponse fromEntity(Cv cv) {
        return fromEntity(cv, false);
    }

    public static CvResponse fromEntity(Cv cv, boolean hasPdf) {
        return CvResponse.builder()
                .id(cv.getId())
                .candidateId(cv.getCandidate() != null ? cv.getCandidate().getId() : null)
                .title(cv.getTitle())
                .isPublic(Boolean.TRUE.equals(cv.getIsPublic()))
                .hasPdf(hasPdf)
                .isWebCreated(!hasPdf)
                .build();
    }
}
