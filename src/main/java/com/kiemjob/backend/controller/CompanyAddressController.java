package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.company.CompanyAddressRequest;
import com.kiemjob.backend.dto.company.CompanyAddressResponse;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.CompanyAddressService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/addresses")
public class CompanyAddressController {

    private final CompanyAddressService addressService;
    private final CurrentUserService currentUserService;

    public CompanyAddressController(
            CompanyAddressService addressService,
            CurrentUserService currentUserService) {
        this.addressService = addressService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CompanyAddressResponse create(
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyAddressRequest request) {
        currentUserService.assertCanManageCompany(companyId);
        return addressService.create(companyId, request);
    }

    @GetMapping
    public List<CompanyAddressResponse> getAll(
            @PathVariable Long companyId) {

        return addressService.getAll(companyId);
    }

    @GetMapping("/{addressId}")
    public CompanyAddressResponse getById(
            @PathVariable Long companyId,
            @PathVariable Long addressId) {

        return addressService.getById(companyId, addressId);
    }

    @PutMapping("/{addressId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CompanyAddressResponse update(
            @PathVariable Long companyId,
            @PathVariable Long addressId,
            @Valid @RequestBody CompanyAddressRequest request) {
        currentUserService.assertCanManageCompany(companyId);
        return addressService.update(companyId, addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public void delete(
            @PathVariable Long companyId,
            @PathVariable Long addressId) {
        currentUserService.assertCanManageCompany(companyId);
        addressService.delete(companyId, addressId);
    }
}
