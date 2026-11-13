package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.skill.SkillCategoryRequest;
import com.kiemjob.backend.dto.skill.SkillCategoryResponse;
import com.kiemjob.backend.entity.SkillCategory;
import com.kiemjob.backend.repository.SkillCategoryRepository;
import com.kiemjob.backend.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillCategoryService {

    private final SkillCategoryRepository categoryRepository;
    private final SkillRepository skillRepository;

    public List<SkillCategoryResponse> getAll() {
        return categoryRepository.findAll()
                .stream()
                .map(SkillCategoryResponse::fromEntity)
                .toList();
    }

    public SkillCategoryResponse getById(Long id) {
        return SkillCategoryResponse.fromEntity(findCategory(id));
    }

    @Transactional
    public SkillCategoryResponse create(SkillCategoryRequest request) {
        String name = request.getName().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Tên nhóm kỹ năng đã tồn tại"
            );
        }

        SkillCategory category = SkillCategory.builder()
                .name(name)
                .build();

        try {
            return SkillCategoryResponse.fromEntity(
                    categoryRepository.saveAndFlush(category)
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Tên nhóm kỹ năng đã tồn tại"
            );
        }
    }

    @Transactional
    public SkillCategoryResponse update(
            Long id, SkillCategoryRequest request) {

        SkillCategory category = findCategory(id);
        String name = request.getName().trim();

        boolean duplicate = categoryRepository.findAll().stream()
                .anyMatch(item ->
                        !item.getId().equals(id)
                        && item.getName().equalsIgnoreCase(name));

        if (duplicate) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Tên nhóm kỹ năng đã tồn tại"
            );
        }

        category.setName(name);

        try {
            return SkillCategoryResponse.fromEntity(
                    categoryRepository.saveAndFlush(category)
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Tên nhóm kỹ năng đã tồn tại"
            );
        }
    }

    @Transactional
    public void delete(Long id) {
        SkillCategory category = findCategory(id);

        if (skillRepository.existsByCategory_Id(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể xóa nhóm đang chứa kỹ năng"
            );
        }

        categoryRepository.delete(category);
    }

    private SkillCategory findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhóm kỹ năng"
                ));
    }
}