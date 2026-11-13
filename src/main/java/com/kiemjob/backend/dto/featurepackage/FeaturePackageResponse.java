package com.kiemjob.backend.dto.featurepackage;

import java.time.LocalDateTime;

public record FeaturePackageResponse(
        Long id,
        String name,
        String description,
        Long price,
        Integer durationDays,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}