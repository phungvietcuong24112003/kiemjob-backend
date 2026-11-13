package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    // ─── Fetch-join queries (tránh N+1) ───

    @Query("SELECT i FROM Interview i " +
           "JOIN FETCH i.application a " +
           "JOIN FETCH a.jobPost jp " +
           "JOIN FETCH jp.company " +
           "JOIN FETCH a.cv cv " +
           "JOIN FETCH cv.candidate " +
           "JOIN FETCH i.recruiter " +
           "WHERE a.id = :applicationId " +
           "ORDER BY i.scheduledAt ASC")
    List<Interview> findByApplicationIdWithDetails(
            @Param("applicationId") Long applicationId);

    @Query("SELECT i FROM Interview i " +
           "JOIN FETCH i.application a " +
           "JOIN FETCH a.jobPost jp " +
           "JOIN FETCH jp.company " +
           "JOIN FETCH a.cv cv " +
           "JOIN FETCH cv.candidate " +
           "JOIN FETCH i.recruiter r " +
           "WHERE r.companyId = :companyId " +
           "ORDER BY i.scheduledAt ASC")
    List<Interview> findByRecruiterCompanyIdWithDetails(
            @Param("companyId") Long companyId);

    @Query("SELECT i FROM Interview i " +
           "JOIN FETCH i.application a " +
           "JOIN FETCH a.jobPost jp " +
           "JOIN FETCH jp.company " +
           "JOIN FETCH a.cv cv " +
           "JOIN FETCH cv.candidate " +
           "JOIN FETCH i.recruiter " +
           "WHERE i.id = :id")
    Optional<Interview> findByIdWithDetails(@Param("id") Long id);
}
