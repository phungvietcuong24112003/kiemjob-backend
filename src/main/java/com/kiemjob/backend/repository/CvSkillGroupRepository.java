package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvSkillGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvSkillGroupRepository
        extends JpaRepository<CvSkillGroup, Long> {

    List<CvSkillGroup> findAllByCvIdOrderByDisplayOrderAscIdAsc(
        Long cvId
    );

    Optional<CvSkillGroup> findByIdAndCvId(
        Long id,
        Long cvId
    );

    int countByCvId(Long cvId);

    void deleteAllByCvId(Long cvId);
}