
package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.company.CompanyLinkRequest;
import com.kiemjob.backend.dto.company.CompanyLinkResponse;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.CompanyLink;
import com.kiemjob.backend.repository.CompanyLinkRepository;
import com.kiemjob.backend.repository.CompanyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CompanyLinkService {

    private final CompanyLinkRepository linkRepository;
    private final CompanyRepository companyRepository;

    public CompanyLinkService(
            CompanyLinkRepository linkRepository,
            CompanyRepository companyRepository) {
        this.linkRepository = linkRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional
    public CompanyLinkResponse create(
            Long companyId, CompanyLinkRequest request) {
        Company company = getCompany(companyId);

        CompanyLink link = new CompanyLink();
        link.setCompany(company);
        applyRequest(link, request);

        return toResponse(linkRepository.save(link));
    }

    @Transactional(readOnly = true)
    public List<CompanyLinkResponse> getAll(Long companyId) {
        getCompany(companyId);

        return linkRepository.findAllByCompany_IdOrderByIdAsc(companyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CompanyLinkResponse getById(Long companyId, Long id) {
        return toResponse(getLink(companyId, id));
    }

    @Transactional
    public CompanyLinkResponse update(
            Long companyId, Long id, CompanyLinkRequest request) {
        CompanyLink link = getLink(companyId, id);
        applyRequest(link, request);

        return toResponse(linkRepository.save(link));
    }

    @Transactional
    public void delete(Long companyId, Long id) {
        linkRepository.delete(getLink(companyId, id));
    }

    private Company getCompany(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy công ty"));
    }

    private CompanyLink getLink(Long companyId, Long id) {
        return linkRepository.findByIdAndCompany_Id(id, companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy liên kết thuộc công ty này"));
    }

    private void applyRequest(
            CompanyLink link, CompanyLinkRequest request) {
        link.setType(request.type().trim().toLowerCase());
        link.setLink(request.link().trim());
    }

    private CompanyLinkResponse toResponse(CompanyLink link) {
        return new CompanyLinkResponse(
                link.getId(),
                link.getCompany().getId(),
                link.getType(),
                link.getLink()
        );
    }
}
