package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.candidate.CandidateLinkRequest;
import com.kiemjob.backend.dto.candidate.CandidateLinkResponse;
import com.kiemjob.backend.service.CandidateLinkService;
import com.kiemjob.backend.service.CvOwnershipService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/candidates/me/links", "/api/candidates/{candidateId}/links"})
public class CandidateLinkController {

    private final CandidateLinkService service;
    private final CvOwnershipService cvOwnershipService;

    public CandidateLinkController(CandidateLinkService service, CvOwnershipService cvOwnershipService) {
        this.service = service;
        this.cvOwnershipService = cvOwnershipService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CANDIDATE')")
    public CandidateLinkResponse create(
            Authentication authentication,
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @Valid @RequestBody CandidateLinkRequest request) {

        if (candidateId != null) {
            cvOwnershipService.checkCandidateOwnership(candidateId);
        }
        return service.create(authentication.getName(), request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public List<CandidateLinkResponse> getAll(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            Authentication authentication) {

        if (candidateId != null) {
            cvOwnershipService.checkCandidateReadAccess(candidateId);
            return service.getAllByCandidateId(candidateId);
        }
        return service.getAll(authentication.getName());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public CandidateLinkResponse getById(
            Authentication authentication,
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable Long id) {

        if (candidateId != null) {
            cvOwnershipService.checkCandidateReadAccess(candidateId);
        }
        return service.getById(authentication.getName(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public CandidateLinkResponse update(
            Authentication authentication,
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable Long id,
            @Valid @RequestBody CandidateLinkRequest request) {

        if (candidateId != null) {
            cvOwnershipService.checkCandidateOwnership(candidateId);
        }
        return service.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CANDIDATE')")
    public void delete(
            Authentication authentication,
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable Long id) {

        if (candidateId != null) {
            cvOwnershipService.checkCandidateOwnership(candidateId);
        }
        service.delete(authentication.getName(), id);
    }
}
