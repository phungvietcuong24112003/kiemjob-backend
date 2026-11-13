package com.kiemjob.backend.dto.cv;

import com.fasterxml.jackson.databind.JsonNode;
import com.kiemjob.backend.entity.CvAnalysis;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class CvAnalysisResponse {

    private Long id;
    private Long cvId;
    private JsonNode parsedData;
    private String summary;
    private String embedding;
    private BigDecimal qualityScore;
    private String suggestions;
    private String analysisStatus;
    private LocalDateTime analyzedAt;
    private String contentHash;
    private String promptVersion;
    private Integer attemptCount;
    private String lastError;
    private LocalDateTime updatedAt;
    private String dataSource;

    public static CvAnalysisResponse fromEntity(CvAnalysis analysis) {
        String source = null;
        if (analysis.getParsedData() != null && analysis.getParsedData().has("dataSource")) {
            source = analysis.getParsedData().get("dataSource").asText(null);
        }
        return CvAnalysisResponse.builder()
                .id(analysis.getId())
                .cvId(analysis.getCv() != null ? analysis.getCv().getId() : null)
                .parsedData(analysis.getParsedData())
                .summary(analysis.getSummary())
                .embedding(analysis.getEmbedding())
                .qualityScore(analysis.getQualityScore())
                .suggestions(analysis.getSuggestions())
                .analysisStatus(analysis.getAnalysisStatus())
                .analyzedAt(analysis.getAnalyzedAt())
                .contentHash(analysis.getContentHash())
                .promptVersion(analysis.getPromptVersion())
                .attemptCount(analysis.getAttemptCount())
                .lastError(analysis.getLastError())
                .updatedAt(analysis.getUpdatedAt())
                .dataSource(source)
                .build();
    }
}
