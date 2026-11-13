package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.JobAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface JobAddressRepository
        extends JpaRepository<JobAddress, Long> {

    List<JobAddress> findByJobPost_Id(Long jobPostId);

    List<JobAddress> findByJobPost_IdIn(List<Long> jobPostIds);

    @Modifying
    @Transactional
    void deleteByJobPost_Id(Long jobPostId);

    boolean existsByJobPost_IdAndProvince_IdAndAddressDetail(
            Long jobPostId,
            Long provinceId,
            String addressDetail
    );

    @Query("""
        SELECT COUNT(ja) > 0 FROM JobAddress ja
        WHERE ja.jobPost.id = :jobPostId
          AND ja.province.id = :provinceId
          AND ((:addressDetail IS NULL AND ja.addressDetail IS NULL) OR ja.addressDetail = :addressDetail)
    """)
    boolean existsAddress(
            @Param("jobPostId") Long jobPostId,
            @Param("provinceId") Long provinceId,
            @Param("addressDetail") String addressDetail
    );

    @Query("""
        SELECT COUNT(ja) > 0 FROM JobAddress ja
        WHERE ja.jobPost.id = :jobPostId
          AND ja.province.id = :provinceId
          AND ((:addressDetail IS NULL AND ja.addressDetail IS NULL) OR ja.addressDetail = :addressDetail)
          AND ja.id != :id
    """)
    boolean existsAddressExcept(
            @Param("jobPostId") Long jobPostId,
            @Param("provinceId") Long provinceId,
            @Param("addressDetail") String addressDetail,
            @Param("id") Long id
    );
}