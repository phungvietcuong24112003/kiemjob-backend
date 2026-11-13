package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CvAnalysisRepository
        extends JpaRepository<CvAnalysis, Long> {

    Optional<CvAnalysis> findByCv_Id(Long cvId);

    boolean existsByCv_Id(Long cvId);

    void deleteByCv_Id(Long cvId);

    List<CvAnalysis> findByCv_IdOrderByIdDesc(Long cvId);

    Optional<CvAnalysis> findByIdAndCv_Id(Long id, Long cvId);

    @Query("SELECT c.id FROM Cv c " +
           "LEFT JOIN CvAnalysis ca ON c.id = ca.cv.id " +
           "WHERE c.isPublic = true " +
           "AND (ca IS NULL OR (ca.analysisStatus IN ('failed', 'pending') AND ca.attemptCount < 3 AND (ca.lastError IS NULL OR ca.lastError != 'NO_TEXT')))")
    List<Long> findCvIdsNeedingAnalysis();
}
