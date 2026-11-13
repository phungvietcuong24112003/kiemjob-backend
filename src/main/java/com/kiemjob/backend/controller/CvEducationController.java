package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvEducationRequest;
import com.kiemjob.backend.dto.cv.CvEducationResponse;
import com.kiemjob.backend.service.CvEducationService;
import com.kiemjob.backend.service.CvOwnershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates/{candidateId}/cvs/{cvId}/educations")
@RequiredArgsConstructor
public class CvEducationController {

    private final CvEducationService educationService;
    private final CvOwnershipService cvOwnershipService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public List<CvEducationResponse> getAll(
            @PathVariable Long candidateId,
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return educationService.getAll(candidateId, cvId);
    }

    @GetMapping("/{educationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvEducationResponse getById(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long educationId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return educationService.getById(
                candidateId, cvId, educationId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvEducationResponse create(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @Valid @RequestBody CvEducationRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return educationService.create(candidateId, cvId, request);
    }

    @PutMapping("/{educationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvEducationResponse update(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long educationId,
            @Valid @RequestBody CvEducationRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return educationService.update(
                candidateId, cvId, educationId, request
        );
    }

    @DeleteMapping("/{educationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public void delete(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long educationId) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        educationService.delete(candidateId, cvId, educationId);
    }
}
