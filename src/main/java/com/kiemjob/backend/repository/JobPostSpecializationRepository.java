package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.JobPostSpecialization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostSpecializationRepository
        extends JpaRepository<JobPostSpecialization, Long> {

    List<JobPostSpecialization>
    findByJobPost_IdOrderBySpecialization_NameAsc(Long jobPostId);

    Optional<JobPostSpecialization> findByJobPost_IdAndSpecialization_Id(
            Long jobPostId,
            Long specializationId
    );

    boolean existsByJobPost_IdAndSpecialization_Id(
            Long jobPostId,
            Long specializationId
    );

    void deleteByJobPost_Id(Long jobPostId);

    void deleteBySpecialization_Id(Long specializationId);
}