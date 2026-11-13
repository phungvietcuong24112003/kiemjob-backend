package com.kiemjob.backend.scheduler;

import com.kiemjob.backend.repository.CvAnalysisRepository;
import com.kiemjob.backend.service.CvAutoAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler tự động quét và thử lại phân tích AI cho các CV công khai (is_public = true)
 * đang có trạng thái failed hoặc pending với số lần thử (attempt_count) < 3.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class CvAnalysisScheduler {

    private final CvAnalysisRepository cvAnalysisRepository;
    private final CvAutoAnalysisService autoAnalysisService;

    /**
     * Chạy mỗi 10 phút: Quét và gọi phân tích lại cho các CV public cần xử lý.
     * Sử dụng ShedLock để bảo đảm trong môi trường đa instance chỉ có 1 node thực thi.
     */
    @Scheduled(cron = "${app.scheduler.retry-cv-analysis-cron:0 0/10 * * * *}")
    @SchedulerLock(
            name = "CvAnalysisScheduler_retryFailedOrPendingAnalyses",
            lockAtLeastFor = "PT2M",
            lockAtMostFor = "PT9M"
    )
    public void retryFailedOrPendingAnalyses() {
        List<Long> cvIds = cvAnalysisRepository.findCvIdsNeedingAnalysis();
        if (cvIds.isEmpty()) {
            return;
        }

        log.info("CvAnalysisScheduler: Bắt đầu xử lý thử lại cho {} CV public cần phân tích", cvIds.size());
        for (Long cvId : cvIds) {
            try {
                autoAnalysisService.analyzeCvAsync(cvId);
            } catch (Exception ex) {
                log.error("CvAnalysisScheduler: Lỗi khi thử lại phân tích cho CV ID {}: {}", cvId, ex.getMessage());
            }
        }
    }
}

