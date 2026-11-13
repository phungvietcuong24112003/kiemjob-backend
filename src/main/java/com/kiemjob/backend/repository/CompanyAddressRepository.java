
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CompanyAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyAddressRepository
        extends JpaRepository<CompanyAddress, Long> {

    List<CompanyAddress> findAllByCompany_IdOrderByIdAsc(Long companyId);

    Optional<CompanyAddress> findByIdAndCompany_Id(
            Long id,
            Long companyId
    );
}
