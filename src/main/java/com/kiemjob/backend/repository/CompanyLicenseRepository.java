package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CompanyLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyLicenseRepository extends JpaRepository<CompanyLicense, Long> {
    List<CompanyLicense> findAllByCompany_IdOrderByIdAsc(Long companyId);
}
