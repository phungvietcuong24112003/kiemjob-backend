// CvCertificateRepository.java
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.CvCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CvCertificateRepository
        extends JpaRepository<CvCertificate, Long> {
    List<CvCertificate> findAllByCvIdOrderByCertificateDateDesc(Long cvId);
}