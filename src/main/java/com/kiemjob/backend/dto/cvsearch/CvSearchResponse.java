package com.kiemjob.backend.dto.cvsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvSearchResponse {

    private SearchFilters parsedFilters;
    private List<CvSearchResult> results;
    private int totalFound;
    private long searchTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchFilters {
        private String industry;
        private List<String> titles;
        private List<String> skills;
        private Double minYears;
        private Double maxYears;
        private String educationLevel;
        private String location;
        private List<String> keywords;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CvSearchResult {
        private Long cvId;
        private Long candidateId;
        private String fullName;
        private String headline;
        private String avatarUrl;
        private String province;
        private Double yearsExperience;
        private String seniority;
        private BigDecimal qualityScore;
        private Integer matchScore;
        private String reason;
        private List<String> matchedSkills;
        private List<String> allSkills;
        private String summary;
        // KHÔNG có email, phone
    }
}

