package com.kiemjob.backend.dto.profile;

import com.kiemjob.backend.entity.Candidate;

import java.time.LocalDate;

public record CandidateProfileResponse(
        Long id,
        Long accountId,
        String fullname,
        String avatarUrl,
        String headline,
        String gender,
        String phone,
        LocalDate dateOfBirth,
        Long provinceId,
        String provinceName,
        String addressDetail,
        String email
) {
    public static CandidateProfileResponse from(Candidate candidate, String provinceName) {
        return from(candidate, provinceName, null);
    }

    public static CandidateProfileResponse from(Candidate candidate, String provinceName, String email) {
        return new CandidateProfileResponse(
                candidate.getId(),
                candidate.getAccountId(),
                candidate.getFullname(),
                candidate.getAvatarUrl(),
                candidate.getHeadline(),
                candidate.getGender(),
                candidate.getPhone(),
                candidate.getDateOfBirth(),
                candidate.getProvinceId(),
                provinceName,
                candidate.getAddressDetail(),
                email
        );
    }
}
