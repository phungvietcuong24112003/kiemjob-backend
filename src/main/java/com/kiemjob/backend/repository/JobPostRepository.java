package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.JobPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface JobPostRepository extends JpaRepository<JobPost, Long>, JpaSpecificationExecutor<JobPost> {

    @Override
    @EntityGraph(attributePaths = {"company", "category", "postedBy"})
    Page<JobPost> findAll(Specification<JobPost> spec, Pageable pageable);

    @Query("""
        SELECT j FROM JobPost j
        WHERE (:status IS NULL OR j.status = :status)
          AND (:companyId IS NULL OR j.company.id = :companyId)
          AND (:categoryId IS NULL OR j.category.id = :categoryId)
          AND (:provinceId IS NULL OR EXISTS (
              SELECT 1 FROM JobAddress ja WHERE ja.jobPost.id = j.id AND ja.province.id = :provinceId
          ))
        ORDER BY CASE WHEN j.isFeatured = true AND (j.featuredUntil IS NULL OR j.featuredUntil > CURRENT_TIMESTAMP) THEN 1 ELSE 0 END DESC,
                 j.postedAt DESC NULLS LAST, j.id DESC
        """)
    List<JobPost> search(
            @Param("status") String status,
            @Param("companyId") Long companyId,
            @Param("categoryId") Long categoryId,
            @Param("provinceId") Long provinceId
    );

    default List<JobPost> search(
            String status,
            Long companyId,
            Long categoryId
    ) {
        return search(status, companyId, categoryId, null);
    }

    @Query("""
        SELECT j FROM JobPost j
        WHERE j.status = 'open'
          AND j.isFeatured = true
          AND (j.featuredUntil IS NULL OR j.featuredUntil > CURRENT_TIMESTAMP)
          AND (:categoryId IS NULL OR j.category.id = :categoryId)
        ORDER BY j.featuredUntil DESC NULLS LAST, j.postedAt DESC NULLS LAST, j.id DESC
        """)
    List<JobPost> findFeaturedJobs(@Param("categoryId") Long categoryId);

    List<JobPost> findByCompany_IdOrderByPostedAtDesc(Long companyId);

    List<JobPost> findByCategory_IdOrderByPostedAtDesc(Long categoryId);

    List<JobPost> findByStatusOrderByPostedAtDesc(String status);

    long countByStatus(String status);

    // =================================================================
    // Lifecycle Scheduler Queries & Bulk Updates
    // =================================================================

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE JobPost j
        SET j.status = 'expired'
        WHERE j.status = 'open'
          AND j.applicationDeadline IS NOT NULL
          AND j.applicationDeadline < :today
        """)
    int expireJobsPastDeadline(@Param("today") LocalDate today);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE JobPost j
        SET j.isFeatured = false
        WHERE j.isFeatured = true
          AND j.featuredUntil IS NOT NULL
          AND j.featuredUntil < :now
        """)
    int clearExpiredFeaturedStatus(@Param("now") LocalDateTime now);

    @Query("""
        SELECT j FROM JobPost j
        JOIN FETCH j.postedBy r
        JOIN FETCH j.company c
        WHERE j.status = 'open'
          AND j.expirationReminderSent = false
          AND j.applicationDeadline IS NOT NULL
          AND j.applicationDeadline >= :today
          AND j.applicationDeadline <= :targetDate
        """)
    List<JobPost> findJobsExpiringSoon(
            @Param("today") LocalDate today,
            @Param("targetDate") LocalDate targetDate
    );

    @Query("""
        SELECT j FROM JobPost j
        JOIN FETCH j.postedBy r
        JOIN FETCH j.company c
        WHERE j.isFeatured = true
          AND j.featuredReminderSent = false
          AND j.featuredUntil IS NOT NULL
          AND j.featuredUntil >= :now
          AND j.featuredUntil <= :targetTime
        """)
    List<JobPost> findFeaturedExpiringSoon(
            @Param("now") LocalDateTime now,
            @Param("targetTime") LocalDateTime targetTime
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobPost j SET j.expirationReminderSent = true WHERE j.id IN :ids")
    int markExpirationRemindersSent(@Param("ids") List<Long> ids);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobPost j SET j.featuredReminderSent = true WHERE j.id IN :ids")
    int markFeaturedRemindersSent(@Param("ids") List<Long> ids);
}
