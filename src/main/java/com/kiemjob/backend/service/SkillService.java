package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.skill.SkillRequest;
import com.kiemjob.backend.dto.skill.SkillResponse;
import com.kiemjob.backend.entity.Skill;
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
public class SkillService {

    private final SkillRepository skillRepository;
    private final SkillCategoryRepository categoryRepository;

    public List<SkillResponse> getAll() {
        return skillRepository.findAllByOrderByNameAsc()
                .stream()
                .map(SkillResponse::fromEntity)
                .toList();
    }

    public SkillResponse getById(Long id) {
        return SkillResponse.fromEntity(findSkill(id));
    }

    public List<SkillResponse> getByCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy nhóm kỹ năng"
            );
        }

        return skillRepository.findByCategory_IdOrderByNameAsc(categoryId)
                .stream()
                .map(SkillResponse::fromEntity)
                .toList();
    }

    @Transactional
    public SkillResponse create(SkillRequest request) {
        SkillCategory category = findCategory(request.getCategoryId());
        String name = request.getName().trim();

        if (skillRepository.existsByCategory_IdAndNameIgnoreCase(
                category.getId(), name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Kỹ năng đã tồn tại trong nhóm này"
            );
        }

        Skill skill = Skill.builder()
                .category(category)
                .name(name)
                .description(normalizeDescription(request.getDescription()))
                .build();

        return saveSkill(skill);
    }

    @Transactional
    public SkillResponse update(Long id, SkillRequest request) {
        Skill skill = findSkill(id);
        SkillCategory category = findCategory(request.getCategoryId());
        String name = request.getName().trim();

        if (skillRepository
                .existsByCategory_IdAndNameIgnoreCaseAndIdNot(
                        category.getId(), name, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Kỹ năng đã tồn tại trong nhóm này"
            );
        }

        skill.setCategory(category);
        skill.setName(name);
        skill.setDescription(normalizeDescription(request.getDescription()));

        return saveSkill(skill);
    }

    @Transactional
    public void delete(Long id) {
        skillRepository.delete(findSkill(id));
    }

    private SkillResponse saveSkill(Skill skill) {
        try {
            return SkillResponse.fromEntity(
                    skillRepository.saveAndFlush(skill)
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên kỹ năng đã tồn tại trong nhóm này"
            );
        }
    }

    private Skill findSkill(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy kỹ năng"
                ));
    }

    private SkillCategory findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy nhóm kỹ năng"
                ));
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank()
                ? null : description.trim();
    }
}