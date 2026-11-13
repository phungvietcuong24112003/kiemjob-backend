
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    List<SavedJob> findByCandidate_IdOrderByIdDesc(Long candidateId);

    Optional<SavedJob> findByCandidate_IdAndJobPost_Id(
        Long candidateId,
        Long jobId
    );

    boolean existsByCandidate_IdAndJobPost_Id(
        Long candidateId,
        Long jobId
    );

    Optional<SavedJob> findByIdAndCandidate_Id(
        Long id,
        Long candidateId
    );
}
