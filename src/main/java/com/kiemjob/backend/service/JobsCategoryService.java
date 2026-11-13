package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.job.JobsCategoryRequest;
import com.kiemjob.backend.dto.job.JobsCategoryResponse;
import com.kiemjob.backend.entity.JobsCategory;
import com.kiemjob.backend.repository.JobsCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobsCategoryService {

    private final JobsCategoryRepository jobsCategoryRepository;

    // Thêm danh mục
    @Transactional
    public JobsCategoryResponse create(JobsCategoryRequest request) {
        String name = normalizeName(request.getName());

        if (jobsCategoryRepository.existsByName(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên danh mục đã tồn tại"
            );
        }

        JobsCategory category = new JobsCategory();
        category.setName(name);
        category.setDescription(normalizeDescription(request.getDescription()));

        try {
            return JobsCategoryResponse.fromEntity(
                    jobsCategoryRepository.saveAndFlush(category)
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên danh mục đã tồn tại hoặc dữ liệu vi phạm ràng buộc"
            );
        }
    }

    // Lấy tất cả danh mục
    @Transactional(readOnly = true)
    public List<JobsCategoryResponse> getAll() {
        return jobsCategoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(JobsCategoryResponse::fromEntity)
                .toList();
    }

    // Lấy danh mục theo ID
    @Transactional(readOnly = true)
    public JobsCategoryResponse getById(Long id) {
        return JobsCategoryResponse.fromEntity(findCategoryById(id));
    }

    // Cập nhật danh mục
    @Transactional
    public JobsCategoryResponse update(
            Long id,
            JobsCategoryRequest request
    ) {
        JobsCategory category = findCategoryById(id);
        String name = normalizeName(request.getName());

        if (jobsCategoryRepository.existsByNameAndIdNot(name, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên danh mục đã tồn tại"
            );
        }

        category.setName(name);
        category.setDescription(normalizeDescription(request.getDescription()));

        try {
            return JobsCategoryResponse.fromEntity(
                    jobsCategoryRepository.saveAndFlush(category)
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên danh mục đã tồn tại hoặc dữ liệu vi phạm ràng buộc"
            );
        }
    }

    // Xóa danh mục
    @Transactional
    public void delete(Long id) {
        JobsCategory category = findCategoryById(id);

        try {
            jobsCategoryRepository.delete(category);
            jobsCategoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể xóa danh mục đang được tin tuyển dụng sử dụng"
            );
        }
    }

    private JobsCategory findCategoryById(Long id) {
        return jobsCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy danh mục có ID: " + id
                ));
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}