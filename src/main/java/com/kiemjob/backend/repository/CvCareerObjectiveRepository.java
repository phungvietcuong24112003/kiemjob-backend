package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvCareerObjective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CvCareerObjectiveRepository
        extends JpaRepository<CvCareerObjective, Long> {

    Optional<CvCareerObjective> findByCvId(Long cvId);
}