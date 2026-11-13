package com.kiemjob.backend.dto.skill;

import com.kiemjob.backend.entity.SkillCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SkillCategoryResponse {

    private Long id;
    private String name;

    public static SkillCategoryResponse fromEntity(SkillCategory category) {
        return SkillCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .build();
    }
}