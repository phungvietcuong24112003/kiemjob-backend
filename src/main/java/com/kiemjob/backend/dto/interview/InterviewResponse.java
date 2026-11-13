package com.kiemjob.backend.dto.interview;

import java.time.LocalDateTime;

public record InterviewResponse(

    Long id,
    Long applicationId,
    Long recruiterId,
    String recruiterName,
    String candidateName,
    String candidateEmail,
    String jobTitle,
    String title,
    LocalDateTime scheduledAt,
    String note,
    LocalDateTime createdAt

) {}