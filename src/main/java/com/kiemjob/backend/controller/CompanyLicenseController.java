package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.company.CompanyLicenseRequest;
import com.kiemjob.backend.dto.company.CompanyLicenseResponse;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.CompanyLicenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/licenses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
public class CompanyLicenseController {

    private final CompanyLicenseService licenseService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyLicenseResponse create(
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyLicenseRequest request
    ) {
        currentUserService.assertCanManageCompany(companyId);
        return licenseService.create(companyId, request);
    }

    // Đọc giấy phép kinh doanh - chỉ ADMIN hoặc Recruiter thuộc công ty đó (không công khai)
    @GetMapping
    public List<CompanyLicenseResponse> getAll(@PathVariable Long companyId) {
        currentUserService.assertCanManageCompany(companyId);
        return licenseService.getAll(companyId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long companyId,
            @PathVariable Long id
    ) {
        currentUserService.assertCanManageCompany(companyId);
        licenseService.delete(companyId, id);
    }
}
