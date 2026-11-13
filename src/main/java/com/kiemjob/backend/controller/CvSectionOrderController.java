package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cv.CvSectionOrderRequest;
import com.kiemjob.backend.dto.cv.CvSectionOrderResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.service.CvOwnershipService;
import com.kiemjob.backend.service.CvSectionOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping({"/api/cvs/{cvId}/section-orders", "/api/candidates/{candidateId}/cvs/{cvId}/section-orders"})
public class CvSectionOrderController {

    private final CvSectionOrderService service;
    private final AccountRepository accountRepository;
    private final CvOwnershipService cvOwnershipService;

    public CvSectionOrderController(
            CvSectionOrderService service,
            AccountRepository accountRepository,
            CvOwnershipService cvOwnershipService) {
        super();
        this.service = service;
        this.accountRepository = accountRepository;
        this.cvOwnershipService = cvOwnershipService;
    }

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
    public List<CvSectionOrderResponse> getAll(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable("cvId") Long cvId,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication) {

        cvOwnershipService.checkCvReadAccess(cvId, candidateId);

        if (candidateId != null) {
            return service.getAllByCandidate(cvId, candidateId);
        }
        Long accountId = resolveAccountId(accountIdAttr, authentication);
        return service.getAll(cvId, accountId);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public List<CvSectionOrderResponse> saveAll(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable Long cvId,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication,
            @Valid @RequestBody CvSectionOrderRequest request) {

        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);

        Long accountId = resolveAccountId(accountIdAttr, authentication);
        return service.saveAll(cvId, accountId, request);
    }

    @PostMapping("/initialize")
    @PreAuthorize("hasAnyRole('ADMIN', 'CANDIDATE')")
    public List<CvSectionOrderResponse> initialize(
            @PathVariable(value = "candidateId", required = false) Long candidateId,
            @PathVariable Long cvId,
            @RequestAttribute(value = "accountId", required = false) Long accountIdAttr,
            Authentication authentication) {

        cvOwnershipService.checkCvWriteAccess(cvId, candidateId);

        Long accountId = resolveAccountId(accountIdAttr, authentication);
        return service.initialize(cvId, accountId);
    }
}
