package com.kiemjob.backend.dto.featuredorder;

import java.time.LocalDateTime;

public record FeaturedOrderResponse(
        Long id,
        Long recruiterId,
        String recruiterName,
        String recruiterEmail,
        Long companyId,
        String companyName,
        String companyLogo,
        Long planId,
        String planName,
        Integer durationDays,
        Long jobId,
        String jobTitle,
        LocalDateTime featuredUntil,
        Long amount,
        String currency,
        String status,
        String paymentProvider,
        String providerTxnId,
        String paymentStatus,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt,
        LocalDateTime refundedAt
) {
}
