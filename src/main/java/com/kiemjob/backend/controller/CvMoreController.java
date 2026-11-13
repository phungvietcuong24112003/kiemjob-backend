package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvMoreRequest;
import com.kiemjob.backend.dto.cv.CvMoreResponse;
import com.kiemjob.backend.service.CvMoreService;
import com.kiemjob.backend.service.CvOwnershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates/{candidateId}/cvs/{cvId}/mores")
@RequiredArgsConstructor
public class CvMoreController {

    private final CvMoreService cvMoreService;
    private final CvOwnershipService cvOwnershipService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvMoreResponse create(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @Valid @RequestBody CvMoreRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return cvMoreService.create(candidateId, cvId, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public List<CvMoreResponse> getAll(
            @PathVariable Long candidateId,
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return cvMoreService.getAll(candidateId, cvId);
    }

    @GetMapping("/{moreId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvMoreResponse getById(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long moreId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return cvMoreService.getById(candidateId, cvId, moreId);
    }

    @PutMapping("/{moreId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvMoreResponse update(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long moreId,
            @Valid @RequestBody CvMoreRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return cvMoreService.update(
                candidateId, cvId, moreId, request
        );
    }

    @DeleteMapping("/{moreId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public void delete(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long moreId) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        cvMoreService.delete(candidateId, cvId, moreId);
    }
}
