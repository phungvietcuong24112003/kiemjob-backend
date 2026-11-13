package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.JobModeration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobModerationRepository
        extends JpaRepository<JobModeration, Long> {

    List<JobModeration> findByJobPost_IdOrderByCreatedAtDescIdDesc(
            Long jobId
    );

    List<JobModeration> findByJobPost_IdInOrderByCreatedAtDescIdDesc(
            List<Long> jobIds
    );

    Optional<JobModeration> findFirstByJobPost_IdOrderByCreatedAtDescIdDesc(
            Long jobId
    );

    long countByJobPost_IdAndCreatedAtAfter(
            Long jobId,
            java.time.LocalDateTime after
    );
}
