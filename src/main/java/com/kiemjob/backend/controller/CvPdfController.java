package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvPdfResponse;
import com.kiemjob.backend.dto.cv.CvPdfUploadResponse;
import com.kiemjob.backend.service.CvOwnershipService;
import com.kiemjob.backend.service.CvPdfService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cvs/{cvId}/pdf")
@RequiredArgsConstructor
public class CvPdfController {

    private final CvPdfService cvPdfService;
    private final CvOwnershipService cvOwnershipService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public ResponseEntity<CvPdfUploadResponse> upload(
            @PathVariable Long cvId,
            @RequestParam("file") MultipartFile file) {
        cvOwnershipService.checkCvWriteAccess(cvId, null);
        return ResponseEntity.ok(cvPdfService.upload(cvId, file));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public ResponseEntity<CvPdfResponse> getCurrent(
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, null);
        return ResponseEntity.ok(cvPdfService.getCurrent(cvId));
    }

    @GetMapping("/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public ResponseEntity<Map<String, String>> download(
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, null);
        String url = cvPdfService.getDownloadUrl(cvId);
        return ResponseEntity.ok(Map.of("downloadUrl", url));
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public ResponseEntity<List<CvPdfResponse>> getVersions(
            @PathVariable Long cvId) {
        cvOwnershipService.checkCvReadAccess(cvId, null);
        return ResponseEntity.ok(cvPdfService.getVersions(cvId));
    }

    @DeleteMapping("/{pdfId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public ResponseEntity<Void> delete(
            @PathVariable Long cvId,
            @PathVariable Long pdfId) {
        cvOwnershipService.checkCvWriteAccess(cvId, null);
        cvPdfService.delete(cvId, pdfId);
        return ResponseEntity.noContent().build();
    }
}
