package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvSectionOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CvSectionOrderRepository
        extends JpaRepository<CvSectionOrder, Long> {

    List<CvSectionOrder> findAllByCvIdOrderByDisplayOrderAscIdAsc(
        Long cvId
    );

    List<CvSectionOrder> findAllByCvId(Long cvId);

    void deleteAllByCvId(Long cvId);
}