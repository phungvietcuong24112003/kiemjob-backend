package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvMore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvMoreRepository extends JpaRepository<CvMore, Long> {

    List<CvMore> findByCv_IdOrderByIdAsc(Long cvId);

    Optional<CvMore> findByIdAndCv_Id(Long id, Long cvId);
}