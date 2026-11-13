
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Recruiter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecruiterRepository extends JpaRepository<Recruiter, Long> {

    Optional<Recruiter> findByAccountId(Long accountId);

    boolean existsByAccountId(Long accountId);

    List<Recruiter> findByCompanyId(Long companyId);

    @Query("""
        SELECT r FROM Recruiter r
        JOIN Account a ON a.id = r.accountId
        WHERE LOWER(a.email) = LOWER(:email)
    """)
    Optional<Recruiter> findByAccountEmailIgnoreCase(
        @Param("email") String email
    );
}
