package com.kiemjob.backend.dto.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SkillCategoryRequest {

    @NotBlank(message = "Tên nhóm kỹ năng không được để trống")
    @Size(max = 100, message = "Tên nhóm tối đa 100 ký tự")
    private String name;
}