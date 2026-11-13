package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.skill.SkillRequest;
import com.kiemjob.backend.dto.skill.SkillResponse;
import com.kiemjob.backend.service.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    public List<SkillResponse> getAll() {
        return skillService.getAll();
    }

    @GetMapping("/{id}")
    public SkillResponse getById(@PathVariable Long id) {
        return skillService.getById(id);
    }

    @GetMapping("/category/{categoryId}")
    public List<SkillResponse> getByCategory(
            @PathVariable Long categoryId) {
        return skillService.getByCategory(categoryId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SkillResponse create(
            @Valid @RequestBody SkillRequest request) {
        return skillService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SkillResponse update(
            @PathVariable Long id,
            @Valid @RequestBody SkillRequest request) {
        return skillService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        skillService.delete(id);
    }
}