package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvEducationRepository
        extends JpaRepository<CvEducation, Long> {

    List<CvEducation> findByCv_IdOrderByStartDateDesc(Long cvId);

    Optional<CvEducation> findByIdAndCv_Id(
            Long id, Long cvId
    );
}