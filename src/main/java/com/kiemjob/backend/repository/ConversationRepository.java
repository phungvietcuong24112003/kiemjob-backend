
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    List<Conversation> findByCandidateIdOrderByIdDesc(Long candidateId);

    List<Conversation> findByRecruiterIdOrderByIdDesc(Long recruiterId);

    Optional<Conversation> findByCandidateIdAndRecruiterId(
            Long candidateId,
            Long recruiterId
    );

    boolean existsByCandidateIdAndRecruiterId(
            Long candidateId,
            Long recruiterId
    );
}
