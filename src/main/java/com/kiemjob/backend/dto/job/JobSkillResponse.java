package com.kiemjob.backend.dto.job;

import com.kiemjob.backend.entity.JobSkill;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobSkillResponse {

    private Long id;
    private Long jobId;
    private Long skillId;
    private String skillName;
    private String description;

    public static JobSkillResponse fromEntity(JobSkill entity) {
        JobSkillResponse response = new JobSkillResponse();

        response.setId(entity.getId());
        response.setJobId(entity.getJobPost().getId());
        response.setSkillId(entity.getSkill().getId());
        response.setSkillName(entity.getSkill().getName());
        response.setDescription(entity.getDescription());

        return response;
    }
}