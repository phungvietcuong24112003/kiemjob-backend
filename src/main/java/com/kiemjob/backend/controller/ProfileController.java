package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.profile.*;
import com.kiemjob.backend.service.CvOwnershipService;
import com.kiemjob.backend.service.ProfileService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final CvOwnershipService cvOwnershipService;

    public ProfileController(ProfileService profileService, CvOwnershipService cvOwnershipService) {
        this.profileService = profileService;
        this.cvOwnershipService = cvOwnershipService;
    }

    // ── Lấy thông tin hồ sơ của tài khoản hiện tại ─────────────
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public MyProfileResponse getMyProfile(Authentication authentication) {
        return profileService.getMyProfile(authentication.getName());
    }

    // ── Lấy hồ sơ ứng viên của chính mình ─────────────────────
    @GetMapping("/candidate")
    @PreAuthorize("hasRole('CANDIDATE')")
    public CandidateProfileResponse getCandidateProfile(Authentication authentication) {
        return profileService.getCandidateProfile(authentication.getName());
    }

    // ── Lấy hồ sơ ứng viên theo ID (cho Recruiter / Admin xem) ──
    @GetMapping("/candidate/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public CandidateProfileResponse getCandidateProfileById(@PathVariable Long id) {
        cvOwnershipService.checkCandidateReadAccess(id);
        return profileService.getCandidateProfileById(id);
    }

    // ── Bổ sung / Cập nhật hồ sơ ứng viên ─────────────────────
    @PutMapping("/candidate")
    @PreAuthorize("hasRole('CANDIDATE')")
    public CandidateProfileResponse updateCandidateProfile(
            Authentication authentication,
            @Valid @RequestBody CandidateProfileRequest request
    ) {
        return profileService.updateCandidateProfile(authentication.getName(), request);
    }

    @PostMapping("/candidate")
    @PreAuthorize("hasRole('CANDIDATE')")
    public CandidateProfileResponse createCandidateProfile(
            Authentication authentication,
            @Valid @RequestBody CandidateProfileRequest request
    ) {
        return profileService.updateCandidateProfile(authentication.getName(), request);
    }

    // ── Lấy hồ sơ nhà tuyển dụng & công ty của chính mình ────
    @GetMapping("/recruiter")
    @PreAuthorize("hasRole('RECRUITER')")
    public RecruiterProfileResponse getRecruiterProfile(Authentication authentication) {
        return profileService.getRecruiterProfile(authentication.getName());
    }

    // ── Bổ sung / Cập nhật hồ sơ nhà tuyển dụng & công ty ────
    @PutMapping("/recruiter")
    @PreAuthorize("hasRole('RECRUITER')")
    public RecruiterProfileResponse updateRecruiterProfile(
            Authentication authentication,
            @Valid @RequestBody RecruiterProfileRequest request
    ) {
        return profileService.updateRecruiterProfile(authentication.getName(), request);
    }

    @PostMapping("/recruiter")
    @PreAuthorize("hasRole('RECRUITER')")
    public RecruiterProfileResponse createRecruiterProfile(
            Authentication authentication,
            @Valid @RequestBody RecruiterProfileRequest request
    ) {
        return profileService.updateRecruiterProfile(authentication.getName(), request);
    }
}
