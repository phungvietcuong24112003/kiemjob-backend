package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.company.CompanyLicenseRequest;
import com.kiemjob.backend.dto.company.CompanyLicenseResponse;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.CompanyLicense;
import com.kiemjob.backend.repository.CompanyLicenseRepository;
import com.kiemjob.backend.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyLicenseService {

    private final CompanyLicenseRepository licenseRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public CompanyLicenseResponse create(Long companyId, CompanyLicenseRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy công ty"));

        CompanyLicense license = new CompanyLicense();
        license.setCompany(company);
        license.setFileUrl(request.fileUrl().trim());

        license = licenseRepository.save(license);
        return new CompanyLicenseResponse(license.getId(), company.getId(), license.getFileUrl());
    }

    @Transactional(readOnly = true)
    public List<CompanyLicenseResponse> getAll(Long companyId) {
        return licenseRepository.findAllByCompany_IdOrderByIdAsc(companyId).stream()
                .map(l -> new CompanyLicenseResponse(l.getId(), l.getCompany().getId(), l.getFileUrl()))
                .toList();
    }

    @Transactional
    public void delete(Long companyId, Long id) {
        CompanyLicense license = licenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giấy phép"));
        if (!license.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giấy phép không thuộc công ty này");
        }
        licenseRepository.delete(license);
    }
}
