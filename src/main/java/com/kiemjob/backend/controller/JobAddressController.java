package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.job.JobAddressRequest;
import com.kiemjob.backend.dto.job.JobAddressResponse;
import com.kiemjob.backend.service.JobAddressService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-addresses")
@RequiredArgsConstructor
public class JobAddressController {

    private final JobAddressService jobAddressService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<JobAddressResponse> create(
            @Valid @RequestBody JobAddressRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobAddressService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobAddressResponse> getById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(jobAddressService.getById(id));
    }

    @GetMapping("/job-post/{jobPostId}")
    public ResponseEntity<List<JobAddressResponse>> getByJobPostId(
            @PathVariable Long jobPostId
    ) {
        return ResponseEntity.ok(
                jobAddressService.getByJobPostId(jobPostId)
        );
    }

    @PutMapping("/job-post/{jobPostId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<List<JobAddressResponse>> syncJobAddresses(
            @PathVariable Long jobPostId,
            @RequestBody List<JobAddressRequest> requests
    ) {
        return ResponseEntity.ok(
                jobAddressService.syncAddresses(jobPostId, requests)
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<JobAddressResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody JobAddressRequest request
    ) {
        return ResponseEntity.ok(
                jobAddressService.update(id, request)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {
        jobAddressService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
