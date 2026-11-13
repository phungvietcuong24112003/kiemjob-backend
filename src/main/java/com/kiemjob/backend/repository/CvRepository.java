package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Cv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CvRepository extends JpaRepository<Cv, Long> {

    List<Cv> findByCandidate_IdOrderByIdDesc(Long candidateId);

    Optional<Cv> findByIdAndCandidate_Id(Long id, Long candidateId);

    boolean existsByIdAndCandidate_Id(Long id, Long candidateId);

    boolean existsByIdAndCandidate_AccountId(Long id, Long accountId);

    long countByCandidate_Id(Long candidateId);

    Optional<Cv> findByCandidate_IdAndIsPublicTrue(Long candidateId);

    @Modifying
    @Query("""
            UPDATE Cv c
            SET c.isPublic = false
            WHERE c.candidate.id = :candidateId
              AND (:excludeCvId IS NULL OR c.id <> :excludeCvId)
            """)
    void resetOtherPublicCvs(
            @Param("candidateId") Long candidateId,
            @Param("excludeCvId") Long excludeCvId);

    @Query("""
                SELECT COUNT(c) > 0
                FROM Cv c
                WHERE c.id = :cvId
                  AND c.candidate.accountId = :accountId
            """)
    boolean existsOwnedCv(
            @Param("cvId") Long cvId,
            @Param("accountId") Long accountId);
}
