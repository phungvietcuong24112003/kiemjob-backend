package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.company.CompanyRequest;
import com.kiemjob.backend.dto.company.CompanyResponse;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.repository.CompanyRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    // Tạo công ty: mặc định Bronze và luôn chờ duyệt
    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        Company company = new Company();

        mapRequest(request, company);
        company.setStatus("pending");
        company.setMembershipTier("bronze");
        company.setTotalSpending(0L);

        return CompanyResponse.fromEntity(
            companyRepository.save(company)
        );
    }

    // Danh sách công ty đã duyệt
    @Transactional(readOnly = true)
    public Page<CompanyResponse> getApproved(int page, int size) {
        validatePagination(page, size);

        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "id")
        );

        return companyRepository.findByStatus("approved", pageable)
            .map(CompanyResponse::fromEntity);
    }

    // Chi tiết công ty
    @Transactional(readOnly = true)
    public CompanyResponse getCompanyById(Long id) {
        return CompanyResponse.fromEntity(findCompany(id));
    }

    // Chi tiết: chỉ công khai công ty đã duyệt
    @Transactional(readOnly = true)
    public CompanyResponse getApprovedById(Long id) {
        Company company = findCompany(id);

        if (!"approved".equals(company.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy công ty"
            );
        }

        return CompanyResponse.fromEntity(company);
    }

    // Tìm kiếm chỉ trong các công ty đã duyệt
    @Transactional(readOnly = true)
    public List<CompanyResponse> searchApproved(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Tên công ty tìm kiếm không được để trống"
            );
        }

        return companyRepository
            .findByStatusAndNameContainingIgnoreCase(
                "approved",
                name.trim(),
                Pageable.unpaged()
            )
            .getContent()
            .stream()
            .map(CompanyResponse::fromEntity)
            .toList();
    }

    // Admin xem danh sách chờ duyệt
    @Transactional(readOnly = true)
    public Page<CompanyResponse> getPending(int page, int size) {
        validatePagination(page, size);

        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.ASC, "id")
        );

        return companyRepository.findByStatus("pending", pageable)
            .map(CompanyResponse::fromEntity);
    }

    // Admin xem danh sách công ty với bộ lọc
    // Hỗ trợ: status, search, membershipTier
    @Transactional(readOnly = true)
    public Page<CompanyResponse> getAdminCompanies(
            String status,
            String search,
            String membershipTier,
            int page,
            int size) {

        validatePagination(page, size);

        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "id")
        );

        String trimmedSearch =
            (search != null && !search.isBlank())
                ? search.trim() : null;

        String trimmedStatus =
            (status != null && !status.isBlank()
                && !"all".equalsIgnoreCase(status))
                ? status.trim().toLowerCase() : null;

        String trimmedTier =
            (membershipTier != null && !membershipTier.isBlank()
                && !"all".equalsIgnoreCase(membershipTier))
                ? membershipTier.trim().toLowerCase() : null;

        if (trimmedTier != null
                && !trimmedTier.equals("bronze")
                && !trimmedTier.equals("silver")
                && !trimmedTier.equals("gold")
                && !trimmedTier.equals("platinum")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Hạng thành viên phải là bronze, silver, gold hoặc platinum"
            );
        }

        if (trimmedStatus != null && trimmedSearch != null
                && trimmedTier != null) {

            return companyRepository
                .findByStatusAndNameContainingIgnoreCaseAndMembershipTier(
                    trimmedStatus,
                    trimmedSearch,
                    trimmedTier,
                    pageable
                )
                .map(CompanyResponse::fromEntity);

        } else if (trimmedStatus != null && trimmedTier != null) {

            return companyRepository
                .findByStatusAndMembershipTier(
                    trimmedStatus,
                    trimmedTier,
                    pageable
                )
                .map(CompanyResponse::fromEntity);

        } else if (trimmedSearch != null && trimmedTier != null) {

            return companyRepository
                .findByNameContainingIgnoreCaseAndMembershipTier(
                    trimmedSearch,
                    trimmedTier,
                    pageable
                )
                .map(CompanyResponse::fromEntity);

        } else if (trimmedTier != null) {

            return companyRepository
                .findByMembershipTier(trimmedTier, pageable)
                .map(CompanyResponse::fromEntity);

        } else if (trimmedStatus != null && trimmedSearch != null) {

            return companyRepository
                .findByStatusAndNameContainingIgnoreCase(
                    trimmedStatus,
                    trimmedSearch,
                    pageable
                )
                .map(CompanyResponse::fromEntity);

        } else if (trimmedStatus != null) {

            return companyRepository.findByStatus(trimmedStatus, pageable)
                .map(CompanyResponse::fromEntity);

        } else if (trimmedSearch != null) {

            return companyRepository.findByNameContainingIgnoreCase(
                trimmedSearch,
                pageable
            ).map(CompanyResponse::fromEntity);

        } else {

            return companyRepository.findAll(pageable)
                .map(CompanyResponse::fromEntity);
        }
    }

    // Admin duyệt công ty
    @Transactional
    public CompanyResponse approve(Long id) {
        Company company = findCompany(id);
        company.setStatus("approved");

        return CompanyResponse.fromEntity(
            companyRepository.save(company)
        );
    }

    // Admin từ chối duyệt hoặc chuyển về chờ duyệt
    @Transactional
    public CompanyResponse reject(Long id) {
        Company company = findCompany(id);
        company.setStatus("pending");

        return CompanyResponse.fromEntity(
            companyRepository.save(company)
        );
    }

    // Cập nhật thông tin, không cho phép thay đổi status và hạng thành viên trực tiếp từ request này
    @Transactional
    public CompanyResponse update(Long id, CompanyRequest request) {
        Company company = findCompany(id);

        mapRequest(request, company);

        return CompanyResponse.fromEntity(
            companyRepository.save(company)
        );
    }

    // Xóa công ty
    @Transactional
    public void delete(Long id) {
        Company company = findCompany(id);
        companyRepository.delete(company);
    }

    /**
     * Tích lũy số tiền thanh toán vào tổng chi tiêu (total_spending) của công ty
     * và tự động nâng hạng thành viên (membership_tier) theo định mức:
     * - Dưới 500.000: bronze
     * - 500.000+: silver
     * - 2.000.000+: gold
     * - 5.000.000+: platinum
     */
    @Transactional
    public Company recordCompanySpending(Long companyId, Long amount) {
        Company company = findCompany(companyId);
        company.addSpending(amount);
        return companyRepository.save(company);
    }

    public Company findCompany(Long id) {
        return companyRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy công ty với id: " + id
            ));
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "page phải >= 0 và size phải từ 1 đến 100"
            );
        }
    }

    private void mapRequest(
            CompanyRequest request,
            Company company) {

        company.setCompanyType(request.getCompanyType());
        company.setName(request.getName().trim());
        company.setLogoUrl(request.getLogoUrl());
        company.setBannerUrl(request.getBannerUrl());
        company.setEmail(request.getEmail());
        company.setPhone(request.getPhone());
        company.setDescription(request.getDescription());
    }
}
