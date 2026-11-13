package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.JobsCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobsCategoryRepository
        extends JpaRepository<JobsCategory, Long> {

    List<JobsCategory> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}