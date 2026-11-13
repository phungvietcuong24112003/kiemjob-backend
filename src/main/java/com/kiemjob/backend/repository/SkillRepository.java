package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    List<Skill> findAllByOrderByNameAsc();

    List<Skill> findByCategory_IdOrderByNameAsc(Long categoryId);

    boolean existsByCategory_IdAndNameIgnoreCase(
        Long categoryId, String name
    );

    boolean existsByCategory_Id(Long categoryId);

    boolean existsByCategory_IdAndNameIgnoreCaseAndIdNot(
    Long categoryId, String name, Long id
);
}