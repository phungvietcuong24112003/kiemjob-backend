package com.kiemjob.backend.dto.skill;

import com.kiemjob.backend.entity.Skill;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SkillResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String description;

    public static SkillResponse fromEntity(Skill skill) {
        return SkillResponse.builder()
                .id(skill.getId())
                .categoryId(skill.getCategory().getId())
                .categoryName(skill.getCategory().getName())
                .name(skill.getName())
                .description(skill.getDescription())
                .build();
    }
}