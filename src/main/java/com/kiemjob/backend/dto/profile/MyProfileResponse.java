package com.kiemjob.backend.dto.profile;

import com.kiemjob.backend.dto.auth.AccountResponse;

public record MyProfileResponse(
        AccountResponse account,
        boolean profileCompleted,
        Object profile
) {
}
