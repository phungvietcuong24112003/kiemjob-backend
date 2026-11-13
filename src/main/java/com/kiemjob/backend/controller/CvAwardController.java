package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvAwardRequest;
import com.kiemjob.backend.dto.cv.CvAwardResponse;
import com.kiemjob.backend.service.CvAwardService;
import com.kiemjob.backend.service.CvOwnershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates/{candidateId}/cvs/{cvId}/awards")
@RequiredArgsConstructor
public class CvAwardController {

    private final CvAwardService awardService;
    private final CvOwnershipService cvOwnershipService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public List<CvAwardResponse> getAll(
            @PathVariable Long candidateId,
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return awardService.getAll(candidateId, cvId);
    }

    @GetMapping("/{awardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvAwardResponse getById(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long awardId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return awardService.getById(candidateId, cvId, awardId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvAwardResponse create(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @Valid @RequestBody CvAwardRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return awardService.create(candidateId, cvId, request);
    }

    @PutMapping("/{awardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvAwardResponse update(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long awardId,
            @Valid @RequestBody CvAwardRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return awardService.update(candidateId, cvId, awardId, request);
    }

    @DeleteMapping("/{awardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public void delete(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long awardId) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        awardService.delete(candidateId, cvId, awardId);
    }
}
