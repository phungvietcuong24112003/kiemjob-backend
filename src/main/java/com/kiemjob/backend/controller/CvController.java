package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvRequest;
import com.kiemjob.backend.dto.cv.CvResponse;
import com.kiemjob.backend.service.CvOwnershipService;
import com.kiemjob.backend.service.CvService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates/{candidateId}/cvs")
@RequiredArgsConstructor
public class CvController {

    private final CvService cvService;
    private final CvOwnershipService cvOwnershipService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public List<CvResponse> getMyCvs(
            @PathVariable Long candidateId) {
        cvOwnershipService.checkCandidateOwnership(candidateId);
        return cvService.getMyCvs(candidateId);
    }

    @GetMapping("/{cvId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvResponse getById(
            @PathVariable Long candidateId,
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return cvService.getById(candidateId, cvId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvResponse create(
            @PathVariable Long candidateId,
            @Valid @RequestBody CvRequest request) {
        cvOwnershipService.checkCandidateOwnership(candidateId);
        return cvService.create(candidateId, request);
    }

    @PutMapping("/{cvId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvResponse update(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @Valid @RequestBody CvRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return cvService.update(candidateId, cvId, request);
    }

    @PatchMapping("/{cvId}/public")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvResponse setPublicStatus(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @RequestParam(name = "isPublic", defaultValue = "true") boolean isPublic) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return cvService.setPublicStatus(candidateId, cvId, isPublic);
    }

    @DeleteMapping("/{cvId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public void delete(
            @PathVariable Long candidateId,
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        cvService.delete(candidateId, cvId);
    }
}
