package com.kiemjob.backend.dto.application;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationAiScoreResponse {

    private Long applicationId;
    private String status; // SUCCESS | FAILED | NO_TEXT
    private Integer overallScore;
    private Integer skillScore;
    private Integer experienceScore;
    private Integer educationScore; // null nếu tin tuyển dụng không yêu cầu học vấn
    private List<String> matchedSkills;
    private List<Map<String, Object>> missingSkills;
    private List<Map<String, Object>> strengths;
    private List<String> gaps;
    private String summary;
    private String model;
    private String promptVersion;
    private LocalDateTime scoredAt;
    private boolean cached;
}

