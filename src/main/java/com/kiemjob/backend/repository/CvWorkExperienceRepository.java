package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvWorkExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvWorkExperienceRepository
        extends JpaRepository<CvWorkExperience, Long> {

    List<CvWorkExperience> findByCv_IdOrderByStartDateDesc(Long cvId);

    Optional<CvWorkExperience> findByIdAndCv_Id(
            Long id,
            Long cvId
    );
}