package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.job.JobSpecializationRequest;
import com.kiemjob.backend.dto.job.JobSpecializationResponse;
import com.kiemjob.backend.service.JobSpecializationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-specializations")
@RequiredArgsConstructor
public class JobSpecializationController {

    private final JobSpecializationService specializationService;

    @GetMapping
    public List<JobSpecializationResponse> getAll(
            @RequestParam(required = false) Long jobCategoryId
    ) {
        return specializationService.getAll(jobCategoryId);
    }

    @GetMapping("/{id}")
    public JobSpecializationResponse getById(@PathVariable Long id) {
        return specializationService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public JobSpecializationResponse create(
            @Valid @RequestBody JobSpecializationRequest request
    ) {
        return specializationService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public JobSpecializationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody JobSpecializationRequest request
    ) {
        return specializationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        specializationService.delete(id);
    }
}