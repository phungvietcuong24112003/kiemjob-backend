package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvWorkExperienceRequest;
import com.kiemjob.backend.dto.cv.CvWorkExperienceResponse;
import com.kiemjob.backend.service.CvOwnershipService;
import com.kiemjob.backend.service.CvWorkExperienceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
    "/api/candidates/{candidateId}/cvs/{cvId}/work-experiences"
)
@RequiredArgsConstructor
public class CvWorkExperienceController {

    private final CvWorkExperienceService experienceService;
    private final CvOwnershipService cvOwnershipService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvWorkExperienceResponse create(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @Valid @RequestBody CvWorkExperienceRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return experienceService.create(candidateId, cvId, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public List<CvWorkExperienceResponse> getAll(
            @PathVariable Long candidateId,
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return experienceService.getAll(candidateId, cvId);
    }

    @GetMapping("/{experienceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvWorkExperienceResponse getById(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long experienceId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return experienceService.getById(
                candidateId, cvId, experienceId
        );
    }

    @PutMapping("/{experienceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvWorkExperienceResponse update(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long experienceId,
            @Valid @RequestBody CvWorkExperienceRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return experienceService.update(
                candidateId, cvId, experienceId, request
        );
    }

    @DeleteMapping("/{experienceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public void delete(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long experienceId) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        experienceService.delete(
                candidateId, cvId, experienceId
        );
    }
}
