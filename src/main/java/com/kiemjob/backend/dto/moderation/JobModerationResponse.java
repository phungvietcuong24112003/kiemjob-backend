package com.kiemjob.backend.dto.moderation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiemjob.backend.entity.JobModeration;

import java.time.LocalDateTime;
import java.util.Map;

public record JobModerationResponse(
        Long id,
        Long jobId,
        Map<String, Object> result,
        String moderationStatus,
        LocalDateTime createdAt,
        Long reviewedById,
        LocalDateTime reviewedAt
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static JobModerationResponse fromEntity(JobModeration entity) {
        Map<String, Object> result = objectMapper.convertValue(
                entity.getResult(),
                new TypeReference<Map<String, Object>>() {}
        );

        return new JobModerationResponse(
                entity.getId(),
                entity.getJobPost().getId(),
                result,
                entity.getModerationStatus(),
                entity.getCreatedAt(),
                entity.getReviewedBy() != null
                        ? entity.getReviewedBy().getId()
                        : null,
                entity.getReviewedAt()
        );
    }
}