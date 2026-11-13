package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.job.JobDetailRequest;
import com.kiemjob.backend.dto.job.JobDetailResponse;
import com.kiemjob.backend.service.JobDetailService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-posts/{jobId}/details")
@RequiredArgsConstructor
public class JobDetailController {

    private final JobDetailService jobDetailService;

    // Thêm mục chi tiết
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<JobDetailResponse> create(
            @PathVariable Long jobId,
            @Valid @RequestBody JobDetailRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobDetailService.create(jobId, request));
    }

    // Lấy danh sách mục chi tiết (Công khai)
    @GetMapping
    public ResponseEntity<List<JobDetailResponse>> getAll(
            @PathVariable Long jobId
    ) {
        return ResponseEntity.ok(
                jobDetailService.getAll(jobId)
        );
    }

    // Lấy một mục chi tiết (Công khai)
    @GetMapping("/{id}")
    public ResponseEntity<JobDetailResponse> getById(
            @PathVariable Long jobId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                jobDetailService.getById(jobId, id)
        );
    }

    // Cập nhật mục chi tiết
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<JobDetailResponse> update(
            @PathVariable Long jobId,
            @PathVariable Long id,
            @Valid @RequestBody JobDetailRequest request
    ) {
        return ResponseEntity.ok(
                jobDetailService.update(jobId, id, request)
        );
    }

    // Xóa mục chi tiết
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<Void> delete(
            @PathVariable Long jobId,
            @PathVariable Long id
    ) {
        jobDetailService.delete(jobId, id);
        return ResponseEntity.noContent().build();
    }
}
