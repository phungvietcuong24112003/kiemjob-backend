package com.kiemjob.backend.dto.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobSpecializationRequest {

    @NotNull(message = "Danh mục nghề nghiệp không được để trống")
    private Long jobCategoryId;

    @NotBlank(message = "Tên chuyên môn không được để trống")
    @Size(max = 100, message = "Tên chuyên môn tối đa 100 ký tự")
    private String name;
}