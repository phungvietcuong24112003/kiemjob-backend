package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvPdfFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvPdfFileRepository
        extends JpaRepository<CvPdfFile, Long> {

    Optional<CvPdfFile> findByCvIdAndIsCurrentTrue(Long cvId);

    List<CvPdfFile> findAllByCvIdOrderByVersionNoDesc(Long cvId);

    Optional<CvPdfFile> findByIdAndCvId(Long id, Long cvId);

    Optional<CvPdfFile> findTopByCvIdOrderByVersionNoDesc(Long cvId);

    boolean existsByCvIdAndIsCurrentTrue(Long cvId);
}