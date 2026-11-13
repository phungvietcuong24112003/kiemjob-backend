package com.kiemjob.backend.dto.cv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvScoreRequest {

    private Long jobPostId;
    private String targetJobTitle;
    private String targetJobLevel;
    private String targetJobDescription;
    private String targetJobRequirements;
}

