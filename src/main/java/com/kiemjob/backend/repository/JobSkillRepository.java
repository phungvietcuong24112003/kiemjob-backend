package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.JobSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobSkillRepository
        extends JpaRepository<JobSkill, Long> {

    List<JobSkill> findByJobPost_IdOrderByIdAsc(Long jobId);

    Optional<JobSkill> findByIdAndJobPost_Id(
            Long id,
            Long jobId
    );

    boolean existsByJobPost_IdAndSkill_Id(
            Long jobId,
            Long skillId
    );

    boolean existsByJobPost_IdAndSkill_IdAndIdNot(
            Long jobId,
            Long skillId,
            Long id
    );
}