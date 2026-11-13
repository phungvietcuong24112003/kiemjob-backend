// CvProjectRepository.java
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvProject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CvProjectRepository extends JpaRepository<CvProject, Long> {
    List<CvProject> findAllByCvIdOrderByStartDateDesc(Long cvId);
}