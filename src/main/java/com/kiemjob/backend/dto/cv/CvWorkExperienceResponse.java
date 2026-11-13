package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.CvWorkExperience;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class CvWorkExperienceResponse {

    private Long id;
    private Long cvId;
    private String companyName;
    private String position;
    private String employmentType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isCurrent;
    private String description;

    public static CvWorkExperienceResponse fromEntity(
            CvWorkExperience experience) {

        return CvWorkExperienceResponse.builder()
                .id(experience.getId())
                .cvId(experience.getCv().getId())
                .companyName(experience.getCompanyName())
                .position(experience.getPosition())
                .employmentType(experience.getEmploymentType())
                .startDate(experience.getStartDate())
                .endDate(experience.getEndDate())
                .isCurrent(experience.getIsCurrent())
                .description(experience.getDescription())
                .build();
    }
}