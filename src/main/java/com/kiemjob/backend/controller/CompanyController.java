package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.company.CompanyRequest;
import com.kiemjob.backend.dto.company.CompanyResponse;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.CompanyService;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final CurrentUserService currentUserService;

    public CompanyController(CompanyService companyService, CurrentUserService currentUserService) {
        this.companyService = companyService;
        this.currentUserService = currentUserService;
    }

    // Tạo công ty - chỉ Admin hoặc Nhà tuyển dụng
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CompanyResponse create(
            @Valid @RequestBody CompanyRequest request) {
        return companyService.create(request);
    }

    // Danh sách công ty đã duyệt (Công khai)
    @GetMapping
    public Page<CompanyResponse> getApproved(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return companyService.getApproved(page, size);
    }

    // Tìm kiếm công ty đã duyệt (Công khai)
    @GetMapping("/search")
    public List<CompanyResponse> search(
            @RequestParam String name) {
        return companyService.searchApproved(name);
    }

    // Chi tiết công ty (Công khai)
    @GetMapping("/{id}")
    public CompanyResponse getById(@PathVariable Long id) {
        return companyService.getCompanyById(id);
    }

    // Admin xem danh sách tất cả công ty với bộ lọc
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<CompanyResponse> getAdminCompanies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String membershipTier,
            @RequestParam(required = false) String subscriptionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String tier = (membershipTier != null && !membershipTier.isBlank()) ? membershipTier : subscriptionType;

        return companyService.getAdminCompanies(
            status,
            search,
            tier,
            page,
            size
        );
    }

    // Admin xem danh sách công ty chờ duyệt
    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<CompanyResponse> getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return companyService.getPending(page, size);
    }

    // Admin duyệt công ty
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public CompanyResponse approve(@PathVariable Long id) {
        return companyService.approve(id);
    }

    // Admin từ chối duyệt hoặc chuyển về chờ duyệt
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public CompanyResponse reject(@PathVariable Long id) {
        return companyService.reject(id);
    }

    // Cập nhật thông tin công ty - chỉ Admin hoặc Nhà tuyển dụng sở hữu công ty đó
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CompanyResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CompanyRequest request) {
        currentUserService.assertCanManageCompany(id);
        return companyService.update(id, request);
    }

    // Xóa công ty - Chỉ Quản trị viên (ADMIN)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        companyService.delete(id);
    }
}
