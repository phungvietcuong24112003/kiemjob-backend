package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvSkillGroupRequest;
import com.kiemjob.backend.dto.cv.CvSkillGroupResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.service.CvOwnershipService;
import com.kiemjob.backend.service.CvSkillGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping({"/api/candidates/{candidateId}/cvs/{cvId}/skill-groups", "/api/cvs/{cvId}/skill-groups"})
@RequiredArgsConstructor
public class CvSkillGroupController {

    private final CvSkillGroupService service;
    private final AccountRepository accountRepository;
    private final CvOwnershipService cvOwnershipService;

    private Long resolveAccountId(Long accountIdAttr, Authentication authentication) {
        if (accountIdAttr != null) {
            return accountIdAttr;
        }
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            return accountRepository.findByEmailIgnoreCase(authentication.getName())
                    .map(Account::getId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn chưa đăng nhập");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public List<CvSkillGroupResponse> getAll(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication) {

        cvOwnershipService.checkCvReadAccess(cvId, candidateId);

        if (candidateId != null) {
            return service.getAll(candidateId, cvId);
        }
        Long accountId = resolveAccountId(accountIdAttr, authentication);
        return service.getAllByAccountId(cvId, accountId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE', 'RECRUITER')")
    public CvSkillGroupResponse getById(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @PathVariable("id") Long id,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication) {

        cvOwnershipService.checkCvReadAccess(cvId, candidateId);

        if (candidateId != null) {
            return service.getById(candidateId, cvId, id);
        }
        Long accountId = resolveAccountId(accountIdAttr, authentication);
        return service.getByIdByAccountId(cvId, id, accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvSkillGroupResponse create(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication,
            @Valid @RequestBody CvSkillGroupRequest request) {

        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);

        if (candidateId != null) {
            return service.create(candidateId, cvId, request);
        }
        Long accountId = resolveAccountId(accountIdAttr, authentication);
        return service.createByAccountId(cvId, accountId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvSkillGroupResponse update(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @PathVariable("id") Long id,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication,
            @Valid @RequestBody CvSkillGroupRequest request) {

        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);

        if (candidateId != null) {
            return service.update(candidateId, cvId, id, request);
        }
        Long accountId = resolveAccountId(accountIdAttr, authentication);
        return service.updateByAccountId(cvId, id, accountId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public void delete(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @PathVariable("id") Long id,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication) {

        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);

        if (candidateId != null) {
            service.delete(candidateId, cvId, id);
            return;
        }
        Long accountId = resolveAccountId(accountIdAttr, authentication);
        service.deleteByAccountId(cvId, id, accountId);
    }
}
