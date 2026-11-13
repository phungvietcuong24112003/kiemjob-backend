package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.CvEducation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class CvEducationResponse {

    private Long id;
    private Long cvId;
    private String schoolName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String rank;
    private String achivements;

    public static CvEducationResponse fromEntity(CvEducation education) {
        return CvEducationResponse.builder()
                .id(education.getId())
                .cvId(education.getCv().getId())
                .schoolName(education.getSchoolName())
                .startDate(education.getStartDate())
                .endDate(education.getEndDate())
                .rank(education.getRank())
                .achivements(education.getAchivements())
                .build();
    }
}