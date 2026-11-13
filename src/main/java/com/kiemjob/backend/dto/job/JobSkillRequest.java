package com.kiemjob.backend.dto.job;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobSkillRequest {

    @NotNull(message = "ID kỹ năng không được để trống")
    @Positive(message = "ID kỹ năng phải lớn hơn 0")
    private Long skillId;

    private String description;
}