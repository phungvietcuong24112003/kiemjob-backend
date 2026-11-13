
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CompanyLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyLinkRepository
        extends JpaRepository<CompanyLink, Long> {

    List<CompanyLink> findAllByCompany_IdOrderByIdAsc(Long companyId);

    Optional<CompanyLink> findByIdAndCompany_Id(
            Long id,
            Long companyId
    );
}
