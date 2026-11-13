package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.featurepackage.FeaturePackageActiveRequest;
import com.kiemjob.backend.dto.featurepackage.FeaturePackageRequest;
import com.kiemjob.backend.dto.featurepackage.FeaturePackageResponse;
import com.kiemjob.backend.entity.FeaturePackage;
import com.kiemjob.backend.repository.FeaturePackageRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FeaturePackageService {

    private final FeaturePackageRepository featurePackageRepository;

    @Transactional(readOnly = true)
    public List<FeaturePackageResponse> getActivePackages() {
        return featurePackageRepository
                .findAllByIsActiveTrueOrderByIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeaturePackageResponse> getAllPackages() {
        return featurePackageRepository
                .findAllByOrderByIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeaturePackageResponse getPackageById(Long id) {
        return toResponse(findPackage(id));
    }

    public FeaturePackageResponse createPackage(
            FeaturePackageRequest request) {

        if (featurePackageRepository.existsByNameIgnoreCase(
                request.name().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên gói dịch vụ đã tồn tại"
            );
        }

        FeaturePackage featurePackage = new FeaturePackage();
        featurePackage.setName(request.name().trim());
        featurePackage.setDescription(request.description());
        featurePackage.setPrice(request.price());
        featurePackage.setDurationDays(request.durationDays());
        featurePackage.setIsActive(true);

        return toResponse(
                featurePackageRepository.save(featurePackage)
        );
    }

    public FeaturePackageResponse updatePackage(
            Long id,
            FeaturePackageRequest request) {

        FeaturePackage featurePackage = findPackage(id);

        if (featurePackageRepository.existsByNameIgnoreCaseAndIdNot(
                request.name().trim(), id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên gói dịch vụ đã được sử dụng"
            );
        }

        featurePackage.setName(request.name().trim());
        featurePackage.setDescription(request.description());
        featurePackage.setPrice(request.price());
        featurePackage.setDurationDays(request.durationDays());

        return toResponse(
                featurePackageRepository.save(featurePackage)
        );
    }

    public FeaturePackageResponse updateActiveStatus(
            Long id,
            FeaturePackageActiveRequest request) {

        FeaturePackage featurePackage = findPackage(id);
        featurePackage.setIsActive(request.isActive());

        return toResponse(
                featurePackageRepository.save(featurePackage)
        );
    }

    public void deactivatePackage(Long id) {
        FeaturePackage featurePackage = findPackage(id);
        featurePackage.setIsActive(false);
        featurePackageRepository.save(featurePackage);
    }

    public void deletePackage(Long id) {
        FeaturePackage featurePackage = findPackage(id);
        try {
            featurePackageRepository.delete(featurePackage);
            featurePackageRepository.flush();
        } catch (Exception ex) {
            // Nếu có ràng buộc khóa ngoại với đơn hàng đã mua, chuyển sang hủy kích hoạt
            featurePackage.setIsActive(false);
            featurePackageRepository.save(featurePackage);
        }
    }

    private FeaturePackage findPackage(Long id) {
        return featurePackageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy gói dịch vụ"
                ));
    }

    private FeaturePackageResponse toResponse(
            FeaturePackage featurePackage) {

        return new FeaturePackageResponse(
                featurePackage.getId(),
                featurePackage.getName(),
                featurePackage.getDescription(),
                featurePackage.getPrice(),
                featurePackage.getDurationDays(),
                featurePackage.getIsActive(),
                featurePackage.getCreatedAt(),
                featurePackage.getUpdatedAt()
        );
    }
}
