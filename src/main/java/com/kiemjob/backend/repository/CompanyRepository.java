package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyRepository
        extends JpaRepository<Company, Long> {

    List<Company> findByNameContainingIgnoreCase(String name);

    Page<Company> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Company> findByStatus(String status, Pageable pageable);

    Page<Company> findByStatusAndNameContainingIgnoreCase(
        String status,
        String name,
        Pageable pageable
    );

    long countByStatus(String status);

    // Lọc theo xếp hạng thành viên (membership tier)
    Page<Company> findByMembershipTier(String membershipTier, Pageable pageable);

    // Lọc theo trạng thái và xếp hạng thành viên
    Page<Company> findByStatusAndMembershipTier(
        String status,
        String membershipTier,
        Pageable pageable
    );

    // Lọc theo tên và xếp hạng thành viên
    Page<Company> findByNameContainingIgnoreCaseAndMembershipTier(
        String name,
        String membershipTier,
        Pageable pageable
    );

    // Lọc theo trạng thái, tên và xếp hạng thành viên
    Page<Company> findByStatusAndNameContainingIgnoreCaseAndMembershipTier(
        String status,
        String name,
        String membershipTier,
        Pageable pageable
    );

    // Đếm số công ty theo xếp hạng thành viên
    long countByMembershipTier(String membershipTier);
}
