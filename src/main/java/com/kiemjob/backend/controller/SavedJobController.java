package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.savedjob.SavedJobRequest;
import com.kiemjob.backend.dto.savedjob.SavedJobResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.service.SavedJobService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/saved-jobs")
@PreAuthorize("hasRole('CANDIDATE')")
public class SavedJobController {

    private final SavedJobService savedJobService;
    private final AccountRepository accountRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedJobResponse saveJob(
            Authentication authentication,
            @Valid @RequestBody SavedJobRequest request) {

        Long accountId = getAccountId(authentication);
        return savedJobService.saveJob(accountId, request);
    }

    @GetMapping
    public List<SavedJobResponse> getSavedJobs(
            Authentication authentication) {

        Long accountId = getAccountId(authentication);
        return savedJobService.getSavedJobs(accountId);
    }

    @GetMapping("/check/{jobId}")
    public boolean isSaved(
            Authentication authentication,
            @PathVariable Long jobId) {

        Long accountId = getAccountId(authentication);
        return savedJobService.isSaved(accountId, jobId);
    }

    @DeleteMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSavedJob(
            Authentication authentication,
            @PathVariable Long jobId) {

        Long accountId = getAccountId(authentication);
        savedJobService.removeSavedJob(accountId, jobId);
    }

    private Long getAccountId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Bạn chưa đăng nhập hoặc phiên đăng nhập không hợp lệ"
            );
        }

        String email = authentication.getName();

        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy tài khoản đăng nhập"
                ));

        return account.getId();
    }
}
