package com.kiemjob.backend.listener;

import com.kiemjob.backend.event.CvAnalysisRequestedEvent;
import com.kiemjob.backend.service.CvAutoAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener lắng nghe sự kiện phân tích CV tự động.
 *
 * RÀNG BUỘC KỸ THUẬT:
 * 1. @TransactionalEventListener(phase = AFTER_COMMIT): Chỉ kích hoạt SAU KHI transaction
 *    cập nhật trạng thái CV (is_public = true hoặc upload PDF) đã commit thành công vào DB.
 * 2. @Async("aiTaskExecutor"): Chạy bất đồng bộ trên thread pool chuyên dụng aiTaskExecutor,
 *    không chặn luồng HTTP request của người dùng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CvAnalysisEventListener {

    private final CvAutoAnalysisService autoAnalysisService;

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCvAnalysisRequested(CvAnalysisRequestedEvent event) {
        log.info("Nhận sự kiện yêu cầu phân tích CV tự động: CV ID={}, source={}",
                event.getCvId(), event.getTriggerSource());
        try {
            autoAnalysisService.analyzeCvAsync(event.getCvId());
        } catch (Exception ex) {
            log.error("Lỗi không mong muốn trong listener phân tích CV ID {}: {}",
                    event.getCvId(), ex.getMessage(), ex);
        }
    }
}

