
package com.kiemjob.backend.dto.payment;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        String provider,
        String providerOrderRef,
        String status,
        Long amount,
        String currency,
        String failureCode,
        String failureMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime refundedAt
) {}
