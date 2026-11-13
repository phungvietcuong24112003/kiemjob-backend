package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.FeaturedOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeaturedOrderRepository
        extends JpaRepository<FeaturedOrder, Long> {

    Optional<FeaturedOrder> findByRecruiter_IdAndIdempotencyKey(
            Long recruiterId,
            String idempotencyKey
    );

    List<FeaturedOrder> findAllByRecruiter_IdOrderByCreatedAtDesc(
            Long recruiterId
    );

    List<FeaturedOrder> findAllByOrderByCreatedAtDesc();

    Optional<FeaturedOrder> findByIdAndRecruiter_Id(
            Long id,
            Long recruiterId
    );
}
