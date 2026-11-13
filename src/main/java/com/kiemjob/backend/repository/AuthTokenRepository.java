package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthTokenRepository
        extends JpaRepository<AuthToken, Long> {

    List<AuthToken> findAllByAccountIdAndPurposeAndUsedAtIsNull(
            Long accountId,
            String purpose
    );

    Optional<AuthToken> findByAccountIdAndPurposeAndTokenHashAndUsedAtIsNull(
            Long accountId,
            String purpose,
            String tokenHash
    );
}