package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.job.JobSpecializationRequest;
import com.kiemjob.backend.dto.job.JobSpecializationResponse;
import com.kiemjob.backend.entity.JobSpecialization;
import com.kiemjob.backend.entity.JobsCategory;
import com.kiemjob.backend.repository.JobSpecializationRepository;
import com.kiemjob.backend.repository.JobPostSpecializationRepository;
import com.kiemjob.backend.repository.JobsCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class JobSpecializationService {

    private final JobSpecializationRepository specializationRepository;
    private final JobPostSpecializationRepository postSpecializationRepository;
    private final JobsCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<JobSpecializationResponse> getAll(Long jobCategoryId) {
        List<JobSpecialization> specializations;

        if (jobCategoryId != null) {
            if (!categoryRepository.existsById(jobCategoryId)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy danh mục nghề nghiệp"
                );
            }

            specializations =
                    specializationRepository
                            .findByJobCategory_IdOrderByNameAsc(jobCategoryId);
        } else {
            specializations =
                    specializationRepository.findAllByOrderByNameAsc();
        }

        return specializations.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobSpecializationResponse getById(Long id) {
        return toResponse(findSpecialization(id));
    }

    public JobSpecializationResponse create(
            JobSpecializationRequest request
    ) {
        JobsCategory category = findCategory(request.getJobCategoryId());
        String name = normalizeName(request.getName());

        if (specializationRepository
                .existsByJobCategory_IdAndNameIgnoreCase(
                        category.getId(), name
                )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chuyên môn đã tồn tại trong danh mục này"
            );
        }

        JobSpecialization specialization = JobSpecialization.builder()
                .jobCategory(category)
                .name(name)
                .build();

        return toResponse(specializationRepository.save(specialization));
    }

    public JobSpecializationResponse update(
            Long id,
            JobSpecializationRequest request
    ) {
        JobSpecialization specialization = findSpecialization(id);
        JobsCategory category = findCategory(request.getJobCategoryId());
        String name = normalizeName(request.getName());

        if (specializationRepository
                .existsByJobCategory_IdAndNameIgnoreCaseAndIdNot(
                        category.getId(), name, id
                )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chuyên môn đã tồn tại trong danh mục này"
            );
        }

        specialization.setJobCategory(category);
        specialization.setName(name);

        return toResponse(specializationRepository.save(specialization));
    }

    public void delete(Long id) {
        JobSpecialization specialization = findSpecialization(id);

        // Xóa các quan hệ trước vì FK specialization_id
        // không khai báo ON DELETE CASCADE trong SQL.
        postSpecializationRepository.deleteBySpecialization_Id(id);

        specializationRepository.delete(specialization);
    }

    private JobSpecialization findSpecialization(Long id) {
        return specializationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chuyên môn"
                ));
    }

    private JobsCategory findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy danh mục nghề nghiệp"
                ));
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tên chuyên môn không được để trống"
            );
        }

        return name.trim();
    }

    private JobSpecializationResponse toResponse(
            JobSpecialization specialization
    ) {
        return JobSpecializationResponse.builder()
                .id(specialization.getId())
                .jobCategoryId(specialization.getJobCategory().getId())
                .jobCategoryName(specialization.getJobCategory().getName())
                .name(specialization.getName())
                .build();
    }
}