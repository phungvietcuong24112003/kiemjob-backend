
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    List<PaymentTransaction> findAllByOrder_IdOrderByCreatedAtDesc(
            Long orderId
    );

    Optional<PaymentTransaction> findByIdAndOrder_IdAndOrder_Recruiter_Id(
            Long paymentId,
            Long orderId,
            Long recruiterId
    );

    boolean existsByOrder_IdAndStatus(Long orderId, String status);
}
