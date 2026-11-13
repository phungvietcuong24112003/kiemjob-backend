package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.job.JobSkillRequest;
import com.kiemjob.backend.dto.job.JobSkillResponse;
import com.kiemjob.backend.service.JobSkillService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-posts/{jobId}/skills")
@RequiredArgsConstructor
public class JobSkillController {

    private final JobSkillService jobSkillService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<JobSkillResponse> create(
            @PathVariable Long jobId,
            @Valid @RequestBody JobSkillRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobSkillService.create(jobId, request));
    }

    @GetMapping
    public ResponseEntity<List<JobSkillResponse>> getAll(
            @PathVariable Long jobId
    ) {
        return ResponseEntity.ok(jobSkillService.getAll(jobId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobSkillResponse> getById(
            @PathVariable Long jobId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(jobSkillService.getById(jobId, id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<JobSkillResponse> update(
            @PathVariable Long jobId,
            @PathVariable Long id,
            @Valid @RequestBody JobSkillRequest request
    ) {
        return ResponseEntity.ok(
                jobSkillService.update(jobId, id, request)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<Void> delete(
            @PathVariable Long jobId,
            @PathVariable Long id
    ) {
        jobSkillService.delete(jobId, id);
        return ResponseEntity.noContent().build();
    }
}
