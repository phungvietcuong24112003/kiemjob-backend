package com.kiemjob.backend.dto.job;

import com.kiemjob.backend.entity.JobsCategory;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobsCategoryResponse {

    private Long id;
    private String name;
    private String description;

    public static JobsCategoryResponse fromEntity(JobsCategory entity) {
        JobsCategoryResponse response = new JobsCategoryResponse();

        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());

        return response;
    }
}