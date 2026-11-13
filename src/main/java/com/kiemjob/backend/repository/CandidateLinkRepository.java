
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CandidateLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateLinkRepository
        extends JpaRepository<CandidateLink, Long> {

    List<CandidateLink> findAllByCandidate_IdOrderByIdAsc(Long candidateId);

    Optional<CandidateLink> findByIdAndCandidate_Id(
            Long id,
            Long candidateId
    );
}
