package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.cvsearch.CvSearchRequest;
import com.kiemjob.backend.dto.cvsearch.CvSearchResponse;
import com.kiemjob.backend.security.AiRateLimiter;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.CvSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recruiter")
public class CvSearchController {

    private final CvSearchService cvSearchService;
    private final AiRateLimiter aiRateLimiter;
    private final CurrentUserService currentUserService;

    /**
     * Tìm kiếm CV ứng viên bằng câu hỏi tự nhiên.
     * Chỉ dành cho nhà tuyển dụng (RECRUITER) và quản trị viên (ADMIN).
     */
    @PostMapping("/cv-search")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<CvSearchResponse> searchCvs(
            @Valid @RequestBody CvSearchRequest request
    ) {
        // Rate limit theo user
        String userIdentifier = currentUserService.currentAccount()
                .map(acc -> acc.getEmail() != null ? acc.getEmail() : String.valueOf(acc.getId()))
                .orElse("anonymous");
        aiRateLimiter.checkRateLimit(userIdentifier);

        int size = request.getSize() != null ? request.getSize() : 10;
        size = Math.max(1, Math.min(20, size));

        log.info("CV Search request: query length={}, size={}", 
                request.getQuery() != null ? request.getQuery().length() : 0, size);

        CvSearchResponse response = cvSearchService.search(request.getQuery(), size);
        return ResponseEntity.ok(response);
    }
}

