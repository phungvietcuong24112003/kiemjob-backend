package com.kiemjob.backend.dto.cv;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateJobPostSpecializationsRequest {

    @NotNull(message = "Danh sách chuyên môn không được để trống")
    private List<@NotNull Long> specializationIds;
}