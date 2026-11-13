
package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.company.CompanyImageRequest;
import com.kiemjob.backend.dto.company.CompanyImageResponse;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.CompanyImage;
import com.kiemjob.backend.repository.CompanyImageRepository;
import com.kiemjob.backend.repository.CompanyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CompanyImageService {

    private final CompanyImageRepository imageRepository;
    private final CompanyRepository companyRepository;

    public CompanyImageService(
            CompanyImageRepository imageRepository,
            CompanyRepository companyRepository) {
        this.imageRepository = imageRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional
    public CompanyImageResponse create(
            Long companyId, CompanyImageRequest request) {
        CompanyImage image = new CompanyImage();
        image.setCompany(getCompany(companyId));
        image.setUrl(request.url().trim());

        return toResponse(imageRepository.save(image));
    }

    @Transactional(readOnly = true)
    public List<CompanyImageResponse> getAll(Long companyId) {
        getCompany(companyId);

        return imageRepository
                .findAllByCompany_IdOrderByIdAsc(companyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CompanyImageResponse getById(Long companyId, Long id) {
        return toResponse(getImage(companyId, id));
    }

    @Transactional
    public CompanyImageResponse update(
            Long companyId, Long id, CompanyImageRequest request) {
        CompanyImage image = getImage(companyId, id);
        image.setUrl(request.url().trim());

        return toResponse(imageRepository.save(image));
    }

    @Transactional
    public void delete(Long companyId, Long id) {
        imageRepository.delete(getImage(companyId, id));
    }

    private Company getCompany(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy công ty"));
    }

    private CompanyImage getImage(Long companyId, Long id) {
        return imageRepository.findByIdAndCompany_Id(id, companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy ảnh thuộc công ty này"));
    }

    private CompanyImageResponse toResponse(CompanyImage image) {
        return new CompanyImageResponse(
                image.getId(),
                image.getCompany().getId(),
                image.getUrl()
        );
    }
}
