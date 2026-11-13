// CvAwardRepository.java
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvAward;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CvAwardRepository extends JpaRepository<CvAward, Long> {
    List<CvAward> findAllByCvIdOrderByAwardDateDesc(Long cvId);
}