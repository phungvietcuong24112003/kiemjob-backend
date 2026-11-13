package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvAnalysisRequest;
import com.kiemjob.backend.dto.cv.CvAnalysisResponse;
import com.kiemjob.backend.dto.cv.CvScoreRequest;
import com.kiemjob.backend.service.CvAnalysisService;
import com.kiemjob.backend.service.CvOwnershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({
    "/api/candidates/{candidateId}/cvs/{cvId}/analyses",
    "/api/cvs/{cvId}/analyses"
})
@RequiredArgsConstructor
public class CvAnalysisController {

    private final CvAnalysisService analysisService;
    private final CvOwnershipService cvOwnershipService;

    @PostMapping("/score")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvAnalysisResponse score(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @RequestBody(required = false) CvScoreRequest request) {
        return analysisService.scoreCv(candidateId, cvId, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvAnalysisResponse create(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @Valid @RequestBody CvAnalysisRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return analysisService.create(candidateId, cvId, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public List<CvAnalysisResponse> getAll(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return analysisService.getAll(candidateId, cvId);
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvAnalysisResponse getLatest(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return analysisService.getByCvId(candidateId, cvId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Chưa có kết quả phân tích cho CV này"));
    }

    @GetMapping("/{analysisId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvAnalysisResponse getById(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @PathVariable("analysisId") Long analysisId) {
        cvOwnershipService.checkCvReadAccess(cvId, candidateId);
        return analysisService.getById(
                candidateId, cvId, analysisId
        );
    }

    @PutMapping("/{analysisId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvAnalysisResponse update(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @PathVariable("analysisId") Long analysisId,
            @Valid @RequestBody CvAnalysisRequest request) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        return analysisService.update(
                candidateId, cvId, analysisId, request
        );
    }

    @DeleteMapping("/{analysisId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public void delete(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @PathVariable("analysisId") Long analysisId) {
        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);
        analysisService.delete(candidateId, cvId, analysisId);
    }
}
