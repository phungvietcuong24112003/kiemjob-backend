package com.kiemjob.backend.dto.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SkillRequest {

    @NotNull(message = "Nhóm kỹ năng không được để trống")
    private Long categoryId;

    @NotBlank(message = "Tên kỹ năng không được để trống")
    @Size(max = 100, message = "Tên kỹ năng tối đa 100 ký tự")
    private String name;

    private String description;
}