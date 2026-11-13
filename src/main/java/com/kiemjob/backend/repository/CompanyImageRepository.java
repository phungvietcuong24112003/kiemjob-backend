
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CompanyImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyImageRepository
        extends JpaRepository<CompanyImage, Long> {

    List<CompanyImage> findAllByCompany_IdOrderByIdAsc(Long companyId);

    Optional<CompanyImage> findByIdAndCompany_Id(
            Long id, Long companyId);
}
