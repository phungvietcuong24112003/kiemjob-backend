package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    Optional<Candidate> findByAccountId(Long accountId);

    boolean existsByAccountId(Long accountId);
}