package com.kiemjob.backend.dto.job;

import com.kiemjob.backend.entity.JobDetail;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobDetailResponse {

    private Long id;
    private Long jobId;
    private String title;
    private String content;

    public static JobDetailResponse fromEntity(JobDetail entity) {
        JobDetailResponse response = new JobDetailResponse();

        response.setId(entity.getId());
        response.setJobId(entity.getJobPost().getId());
        response.setTitle(entity.getTitle());
        response.setContent(entity.getContent());

        return response;
    }
}