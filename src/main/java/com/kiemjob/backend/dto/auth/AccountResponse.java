package com.kiemjob.backend.dto.auth;

import com.kiemjob.backend.entity.Account;

public record AccountResponse(
        Long id,
        String email,
        String role,
        boolean emailVerified,
        boolean profileCompleted
) {
    public static AccountResponse of(Account account, boolean profileCompleted) {
        return new AccountResponse(
                account.getId(),
                account.getEmail(),
                account.getRole(),
                account.isEmailVerified(),
                profileCompleted
        );
    }
}