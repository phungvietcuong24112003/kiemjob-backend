package com.kiemjob.backend.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện Spring phát ra khi một CV cần được phân tích AI ở chế độ nền.
 * Thường kích hoạt khi CV được đặt is_public = true hoặc khi tải lên PDF mới cho CV đang public.
 */
@Getter
@AllArgsConstructor
public class CvAnalysisRequestedEvent {
    private final Long cvId;
    private final String triggerSource; // "SET_PUBLIC", "UPLOAD_PDF", "RETRY_SCHEDULER"
}

