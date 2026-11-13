package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.company.CompanyLinkRequest;
import com.kiemjob.backend.dto.company.CompanyLinkResponse;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.CompanyLinkService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/links")
public class CompanyLinkController {

    private final CompanyLinkService service;
    private final CurrentUserService currentUserService;

    public CompanyLinkController(CompanyLinkService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CompanyLinkResponse create(
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyLinkRequest request) {
        currentUserService.assertCanManageCompany(companyId);
        return service.create(companyId, request);
    }

    @GetMapping
    public List<CompanyLinkResponse> getAll(
            @PathVariable Long companyId) {
        return service.getAll(companyId);
    }

    @GetMapping("/{id}")
    public CompanyLinkResponse getById(
            @PathVariable Long companyId,
            @PathVariable Long id) {
        return service.getById(companyId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CompanyLinkResponse update(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @Valid @RequestBody CompanyLinkRequest request) {
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
