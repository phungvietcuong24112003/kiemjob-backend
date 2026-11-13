package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.SkillCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillCategoryRepository
        extends JpaRepository<SkillCategory, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}