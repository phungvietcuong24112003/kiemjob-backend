package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.interview.InterviewRequest;
import com.kiemjob.backend.dto.interview.InterviewResponse;
import com.kiemjob.backend.service.InterviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN', 'CANDIDATE')")
public class InterviewController {

    private final InterviewService interviewService;

    // Tạo lịch phỏng vấn
    @PostMapping
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<InterviewResponse> create(
            @Valid @RequestBody InterviewRequest request) {

        if (request.applicationId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "applicationId không được để trống");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewService.create(request.applicationId(), request));
    }

    // Lấy tất cả lịch phỏng vấn của công ty recruiter
    @GetMapping("/my-interviews")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<List<InterviewResponse>> getMyCompanyInterviews() {

        return ResponseEntity.ok(interviewService.getMyCompanyInterviews());
    }

    // Lấy lịch phỏng vấn theo hồ sơ ứng tuyển
    @GetMapping("/application/{applicationId}")
    public ResponseEntity<List<InterviewResponse>> getByApplication(
            @PathVariable Long applicationId) {

        return ResponseEntity.ok(interviewService.getByApplication(applicationId));
    }

    // Lấy chi tiết lịch phỏng vấn
    @GetMapping("/{id}")
    public ResponseEntity<InterviewResponse> getById(@PathVariable Long id) {

        return ResponseEntity.ok(interviewService.getById(id));
    }

    // Cập nhật lịch phỏng vấn
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<InterviewResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody InterviewRequest request) {

        return ResponseEntity.ok(interviewService.update(id, request));
    }

    // Xóa lịch phỏng vấn
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {

        interviewService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
