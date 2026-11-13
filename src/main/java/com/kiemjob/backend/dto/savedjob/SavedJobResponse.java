package com.kiemjob.backend.dto.savedjob;

import com.kiemjob.backend.entity.SavedJob;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SavedJobResponse {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private Long candidateId;
    private Long companyId;
    private String companyName;
    private String companyLogo;

    public static SavedJobResponse fromEntity(SavedJob entity) {
        SavedJobResponse response = new SavedJobResponse();

        response.setId(entity.getId());
        if (entity.getJobPost() != null) {
            response.setJobId(entity.getJobPost().getId());
            response.setJobTitle(entity.getJobPost().getTitle());
            if (entity.getJobPost().getCompany() != null) {
                response.setCompanyId(entity.getJobPost().getCompany().getId());
                response.setCompanyName(entity.getJobPost().getCompany().getName());
                response.setCompanyLogo(entity.getJobPost().getCompany().getLogoUrl());
            }
        }
        if (entity.getCandidate() != null) {
            response.setCandidateId(entity.getCandidate().getId());
        }

        return response;
    }
}
