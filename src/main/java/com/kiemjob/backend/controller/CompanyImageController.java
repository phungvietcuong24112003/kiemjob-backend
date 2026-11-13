package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.company.CompanyImageRequest;
import com.kiemjob.backend.dto.company.CompanyImageResponse;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.CompanyImageService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/images")
public class CompanyImageController {

    private final CompanyImageService service;
    private final CurrentUserService currentUserService;

    public CompanyImageController(CompanyImageService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CompanyImageResponse create(
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyImageRequest request) {
        currentUserService.assertCanManageCompany(companyId);
        return service.create(companyId, request);
    }

    @GetMapping
    public List<CompanyImageResponse> getAll(
            @PathVariable Long companyId) {
        return service.getAll(companyId);
    }

    @GetMapping("/{id}")
    public CompanyImageResponse getById(
            @PathVariable Long companyId,
            @PathVariable Long id) {
        return service.getById(companyId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CompanyImageResponse update(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @Valid @RequestBody CompanyImageRequest request) {
        currentUserService.assertCanManageCompany(companyId);
        return service.update(companyId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public void delete(
            @PathVariable Long companyId,
            @PathVariable Long id) {
        currentUserService.assertCanManageCompany(companyId);
        service.delete(companyId, id);
    }
}
