package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.FeaturePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeaturePackageRepository
        extends JpaRepository<FeaturePackage, Long> {

    List<FeaturePackage> findAllByIsActiveTrueOrderByIdDesc();

    List<FeaturePackage> findAllByOrderByIdDesc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}