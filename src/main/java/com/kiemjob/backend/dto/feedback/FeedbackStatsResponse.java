package com.kiemjob.backend.dto.feedback;

import java.util.Map;

public record FeedbackStatsResponse(
        long totalFeedbacks,
        double averageRating,
        long candidateFeedbacks,
        long recruiterFeedbacks,
        Map<Integer, Long> ratingCounts
) {}

