package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvProjectRequest;
import com.kiemjob.backend.dto.cv.CvProjectResponse;
import com.kiemjob.backend.service.CvOwnershipService;
import com.kiemjob.backend.service.CvProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates/{candidateId}/cvs/{cvId}/projects")
@RequiredArgsConstructor
public class CvProjectController {

    private final CvProjectService projectService;
    private final CvOwnershipService cvOwnershipService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public List<CvProjectResponse> getAll(
            @PathVariable Long candidateId,
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return projectService.getAll(candidateId, cvId);
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvProjectResponse getById(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long projectId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return projectService.getById(candidateId, cvId, projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvProjectResponse create(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @Valid @RequestBody CvProjectRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return projectService.create(candidateId, cvId, request);
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvProjectResponse update(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long projectId,
            @Valid @RequestBody CvProjectRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return projectService.update(candidateId, cvId, projectId, request);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public void delete(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long projectId) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        projectService.delete(candidateId, cvId, projectId);
    }
}
