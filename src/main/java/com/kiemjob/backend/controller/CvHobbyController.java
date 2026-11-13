package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvHobbyRequest;
import com.kiemjob.backend.dto.cv.CvHobbyResponse;
import com.kiemjob.backend.service.CvHobbyService;
import com.kiemjob.backend.service.CvOwnershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates/{candidateId}/cvs/{cvId}/hobbies")
@RequiredArgsConstructor
public class CvHobbyController {

    private final CvHobbyService hobbyService;
    private final CvOwnershipService cvOwnershipService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public List<CvHobbyResponse> getAll(
            @PathVariable Long candidateId,
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return hobbyService.getAll(candidateId, cvId);
    }

    @GetMapping("/{hobbyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvHobbyResponse getById(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long hobbyId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return hobbyService.getById(candidateId, cvId, hobbyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvHobbyResponse create(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @Valid @RequestBody CvHobbyRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return hobbyService.create(candidateId, cvId, request);
    }

    @PutMapping("/{hobbyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvHobbyResponse update(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long hobbyId,
            @Valid @RequestBody CvHobbyRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return hobbyService.update(candidateId, cvId, hobbyId, request);
    }

    @DeleteMapping("/{hobbyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public void delete(
            @PathVariable Long candidateId,
            @PathVariable Long cvId,
            @PathVariable Long hobbyId) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        hobbyService.delete(candidateId, cvId, hobbyId);
    }
}
