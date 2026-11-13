package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.JobDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobDetailRepository
        extends JpaRepository<JobDetail, Long> {

    List<JobDetail> findByJobPost_IdOrderByIdAsc(Long jobId);

    Optional<JobDetail> findByIdAndJobPost_Id(
            Long id,
            Long jobId
    );
}