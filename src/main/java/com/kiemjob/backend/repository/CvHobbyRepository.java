// CvHobbyRepository.java
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvHobby;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CvHobbyRepository extends JpaRepository<CvHobby, Long> {
    List<CvHobby> findAllByCvIdOrderByIdAsc(Long cvId);
    boolean existsByCvIdAndHobbyNameIgnoreCase(Long cvId, String hobbyName);
}