package com.kiemjob.backend.dto.job;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobSpecializationResponse {

    private Long id;
    private Long jobCategoryId;
    private String jobCategoryName;
    private String name;
}