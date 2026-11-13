package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.skill.SkillCategoryRequest;
import com.kiemjob.backend.dto.skill.SkillCategoryResponse;
import com.kiemjob.backend.service.SkillCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/skill-categories")
@RequiredArgsConstructor
public class SkillCategoryController {

    private final SkillCategoryService categoryService;

    @GetMapping
    public List<SkillCategoryResponse> getAll() {
        return categoryService.getAll();
    }

    @GetMapping("/{id}")
    public SkillCategoryResponse getById(@PathVariable Long id) {
        return categoryService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SkillCategoryResponse create(
            @Valid @RequestBody SkillCategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SkillCategoryResponse update(
            @PathVariable Long id,
            @Valid @RequestBody SkillCategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}