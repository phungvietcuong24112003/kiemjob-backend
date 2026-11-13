package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvCareerObjectiveRequest;
import com.kiemjob.backend.dto.cv.CvCareerObjectiveResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.service.CvCareerObjectiveService;
import com.kiemjob.backend.service.CvOwnershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping({"/api/cvs/{cvId}/career-objective", "/api/candidates/{candidateId}/cvs/{cvId}/career-objective"})
@RequiredArgsConstructor
public class CvCareerObjectiveController {

    private final CvCareerObjectiveService service;
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
    public CvCareerObjectiveResponse getByCvId(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication) {

        cvOwnershipService.checkCvReadAccess(cvId, candidateId);

        if (candidateId != null) {
            return service.getByCandidate(candidateId, cvId);
        }
        Long accountId = resolveAccountId(accountIdAttr, authentication);
        return service.getByCvId(cvId, accountId);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public CvCareerObjectiveResponse saveOrUpdate(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication,
            @Valid @RequestBody CvCareerObjectiveRequest request) {

        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);

        if (candidateId != null) {
            return service.saveOrUpdateByCandidate(candidateId, cvId, request);
        }
        Long accountId = resolveAccountId(accountIdAttr, authentication);
        return service.saveOrUpdate(cvId, accountId, request);
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public ResponseEntity<Void> delete(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication) {

        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);

        if (candidateId != null) {
            service.deleteByCandidate(candidateId, cvId);
        } else {
            Long accountId = resolveAccountId(accountIdAttr, authentication);
            service.delete(cvId, accountId);
        }

        return ResponseEntity.noContent().build();
    }
}
