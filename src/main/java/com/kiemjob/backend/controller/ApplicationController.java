package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.application.ApplicationAiScoreResponse;
import com.kiemjob.backend.dto.application.ApplicationRequest;
import com.kiemjob.backend.dto.application.ApplicationResponse;
import com.kiemjob.backend.dto.application.ApplicationStatusRequest;
import com.kiemjob.backend.dto.application.BulkApplicationStatusRequest;
import com.kiemjob.backend.dto.application.BulkApplicationStatusResponse;
import com.kiemjob.backend.service.ApplicationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApplicationController {

    private final ApplicationService applicationService;

    // Nộp đơn ứng tuyển - Chỉ ứng viên (CANDIDATE)
    @PostMapping("/job-posts/{jobPostId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApplicationResponse apply(
            @PathVariable Long jobPostId,
            @Valid @RequestBody ApplicationRequest request) {
        return applicationService.apply(jobPostId, request);
    }

    // Danh sách đơn ứng tuyển:
    // - CANDIDATE: chỉ xem đơn của chính mình
    // - ADMIN: có thể lọc theo cvId, candidateId hoặc xem tất cả
    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public List<ApplicationResponse> getApplications(
            @RequestParam(required = false) Long cvId,
            @RequestParam(required = false) Long candidateId) {
        return applicationService.getApplicationsForCurrentUser(cvId, candidateId);
    }

    // Danh sách đơn ứng tuyển của một tin tuyển dụng - Phân trang, lọc status, keyword
    @GetMapping("/job-posts/{jobPostId}/applications")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public Object getByJobPost(
            @PathVariable Long jobPostId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "appliedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false, defaultValue = "false") boolean unpaged) {
        if (unpaged) {
            return applicationService.getByJobPost(jobPostId);
        }
        return applicationService.getByJobPostPaged(jobPostId, status, keyword, pageable);
    }

    // Danh sách đơn ứng tuyển tổng hợp cho recruiter (tất cả các bài đăng của công ty)
    @GetMapping("/recruiter/applications")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public Page<ApplicationResponse> getApplicationsForRecruiter(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "appliedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return applicationService.getApplicationsForRecruiter(jobId, status, keyword, pageable);
    }

    // Chi tiết đơn ứng tuyển - Chỉ candidate chủ đơn, recruiter sở hữu bài hoặc ADMIN
    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public ApplicationResponse getById(@PathVariable Long id) {
        return applicationService.getById(id);
    }

    // Cập nhật trạng thái đơn ứng tuyển đơn lẻ
    @PatchMapping("/applications/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ApplicationResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationStatusRequest request) {
        return applicationService.updateStatus(id, request);
    }

    // Cập nhật trạng thái hàng loạt
    @PatchMapping("/applications/status/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public BulkApplicationStatusResponse bulkUpdateStatus(
            @Valid @RequestBody BulkApplicationStatusRequest request) {
        return applicationService.bulkUpdateStatus(request);
    }

    // Chấm điểm CV so với yêu cầu bài post (on-demand, cache bộ nhớ)
    @PostMapping("/applications/{id}/ai-score")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ApplicationAiScoreResponse scoreApplicationAgainstJob(@PathVariable Long id) {
        return applicationService.scoreApplicationAgainstJob(id);
    }

    // Rút đơn ứng tuyển - Chỉ candidate chủ đơn
    @DeleteMapping("/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CANDIDATE')")
    public void withdraw(@PathVariable Long id) {
        applicationService.withdraw(id);
    }
}
