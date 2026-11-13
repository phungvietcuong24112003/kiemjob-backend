package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.moderation.JobModerationResponse;
import com.kiemjob.backend.service.JobModerationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-posts/{jobId}/moderation")
public class JobModerationController {

    private final JobModerationService jobModerationService;

    public JobModerationController(
            JobModerationService jobModerationService
    ) {
        this.jobModerationService = jobModerationService;
    }

    // Chạy AI kiểm duyệt bài tuyển dụng
    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public JobModerationResponse checkJob(
            @PathVariable Long jobId,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : null;
        return jobModerationService.checkJob(jobId, email);
    }

    // Xem toàn bộ lịch sử kiểm duyệt
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public List<JobModerationResponse> getHistory(
            @PathVariable Long jobId,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : null;
        return jobModerationService.getHistory(jobId, email);
    }

    // Xem lần kiểm duyệt gần nhất
    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public JobModerationResponse getLatest(
            @PathVariable Long jobId,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : null;
        return jobModerationService.getLatest(jobId, email);
    }
}
