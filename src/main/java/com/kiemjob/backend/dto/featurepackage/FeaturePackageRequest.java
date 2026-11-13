package com.kiemjob.backend.dto.featurepackage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FeaturePackageRequest(

        @NotBlank(message = "Tên gói không được để trống")
        @Size(max = 100, message = "Tên gói tối đa 100 ký tự")
        String name,

        String description,

        @NotNull(message = "Giá gói không được để trống")
        @Positive(message = "Giá gói phải lớn hơn 0")
        Long price,

        @NotNull(message = "Thời hạn không được để trống")
        @Positive(message = "Thời hạn phải lớn hơn 0")
        Integer durationDays
) {
}