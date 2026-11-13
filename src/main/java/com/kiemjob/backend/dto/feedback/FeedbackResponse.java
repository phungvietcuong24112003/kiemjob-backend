package com.kiemjob.backend.dto.feedback;

import java.time.LocalDateTime;

public record FeedbackResponse(
        Long id,
        Long accountId,
        String accountEmail,
        String accountRole,
        String senderName,
        String subject,
        Integer rating,
        String content,
        LocalDateTime createdAt
) {}

