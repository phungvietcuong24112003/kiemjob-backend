package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.JobSpecialization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobSpecializationRepository
        extends JpaRepository<JobSpecialization, Long> {

    List<JobSpecialization> findAllByOrderByNameAsc();

    List<JobSpecialization> findByJobCategory_IdOrderByNameAsc(
            Long jobCategoryId
    );

    boolean existsByJobCategory_IdAndNameIgnoreCase(
            Long jobCategoryId,
            String name
    );

    boolean existsByJobCategory_IdAndNameIgnoreCaseAndIdNot(
            Long jobCategoryId,
            String name,
            Long id
    );
}