package com.kiemjob.backend.dto.feedback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FeedbackCreateRequest(
        String subject,

        @NotNull(message = "Đánh giá sao không được để trống")
        @Min(value = 1, message = "Đánh giá tối thiểu 1 sao")
        @Max(value = 5, message = "Đánh giá tối đa 5 sao")
        Integer rating,

        String content,

        // Cho phép gửi linh hoạt từ nhiều form khác nhau (category, email...)
        String category,
        String email
) {
    public String getResolvedSubject() {
        if (subject != null && !subject.trim().isEmpty()) {
            return subject.trim();
        }
        if (category != null && !category.trim().isEmpty()) {
            return category.trim();
        }
        return "Góp ý chung";
    }

    public String getResolvedContent() {
        if (content != null && !content.trim().isEmpty()) {
            return content.trim();
        }
        return "";
    }
}

