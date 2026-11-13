package com.kiemjob.backend.dto.featurepackage;

import jakarta.validation.constraints.NotNull;

public record FeaturePackageActiveRequest(
        @NotNull(message = "Trạng thái hoạt động không được để trống")
        Boolean isActive
) {
}