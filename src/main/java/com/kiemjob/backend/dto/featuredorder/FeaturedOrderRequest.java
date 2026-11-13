package com.kiemjob.backend.dto.featuredorder;

import jakarta.validation.constraints.NotNull;

public record FeaturedOrderRequest(
        @NotNull(message = "Mã gói dịch vụ không được để trống")
        Long planId,
        Long jobId
) {}
