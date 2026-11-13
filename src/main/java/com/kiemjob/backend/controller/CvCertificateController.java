package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvCertificateRequest;
import com.kiemjob.backend.dto.cv.CvCertificateResponse;
import com.kiemjob.backend.service.CvCertificateService;
import com.kiemjob.backend.service.CvOwnershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates/{candidateId}/cvs/{cvId}/certificates")
@RequiredArgsConstructor
public class CvCertificateController {

    private final CvCertificateService certificateService;
    private final CvOwnershipService cvOwnershipService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public List<CvCertificateResponse> getAll(
            @PathVariable Long candidateId,
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return certificateService.getAll(candidateId, cvId);
    }

    @GetMapping("/{certificateId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvCertificateResponse getById(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long certificateId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return certificateService.getById(candidateId, cvId, certificateId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvCertificateResponse create(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @Valid @RequestBody CvCertificateRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return certificateService.create(candidateId, cvId, request);
    }

    @PutMapping("/{certificateId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvCertificateResponse update(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long certificateId,
            @Valid @RequestBody CvCertificateRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return certificateService.update(candidateId, cvId, certificateId, request);
    }

    @DeleteMapping("/{certificateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public void delete(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long certificateId) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        certificateService.delete(candidateId, cvId, certificateId);
    }
}
