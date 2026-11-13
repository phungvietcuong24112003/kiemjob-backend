package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.job.JobsCategoryRequest;
import com.kiemjob.backend.dto.job.JobsCategoryResponse;
import com.kiemjob.backend.service.JobsCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs-categories")
@RequiredArgsConstructor
public class JobsCategoryController {

    private final JobsCategoryService jobsCategoryService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobsCategoryResponse> create(
            @Valid @RequestBody JobsCategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobsCategoryService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<JobsCategoryResponse>> getAll() {
        return ResponseEntity.ok(jobsCategoryService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobsCategoryResponse> getById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(jobsCategoryService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobsCategoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody JobsCategoryRequest request
    ) {
        return ResponseEntity.ok(
                jobsCategoryService.update(id, request)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {
        jobsCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}