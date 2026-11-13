package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findAllByOrderByCreatedAtDesc();
    List<Feedback> findByAccountIdOrderByCreatedAtDesc(Long accountId);
    long countByAccountId(Long accountId);
}

