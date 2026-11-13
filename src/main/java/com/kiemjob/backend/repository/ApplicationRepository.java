package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository
        extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    boolean existsByJobPost_IdAndCv_Id(Long jobPostId, Long cvId);

    List<Application> findByCv_IdOrderByAppliedAtDesc(Long cvId);

    List<Application> findByCv_Candidate_IdOrderByAppliedAtDesc(Long candidateId);

    List<Application> findByCv_Candidate_AccountIdOrderByAppliedAtDesc(Long accountId);

    List<Application> findByJobPost_IdOrderByAppliedAtDesc(Long jobPostId);

    Optional<Application> findByIdAndCv_Id(Long id, Long cvId);

    Optional<Application> findByIdAndJobPost_Id(Long id, Long jobPostId);

    boolean existsByCv_IdAndJobPost_Company_Id(Long cvId, Long companyId);

    boolean existsByCv_Candidate_IdAndJobPost_Company_Id(Long candidateId, Long companyId);

    boolean existsByIdAndJobPost_PostedBy_Id(
            Long applicationId,
            Long recruiterId);

    List<Application> findByIdIn(List<Long> ids);
}
