package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProvinceRepository
        extends JpaRepository<Province, Long> {

    List<Province> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}