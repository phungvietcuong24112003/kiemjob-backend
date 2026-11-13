package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.job.JobPostRequest;
import com.kiemjob.backend.dto.job.JobPostResponse;
import com.kiemjob.backend.service.JobPostService;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/job-posts")
public class JobPostController {

    private final JobPostService jobPostService;

    public JobPostController(JobPostService jobPostService) {
        super();
        this.jobPostService = jobPostService;
    }

    // Duyệt / Đổi trạng thái tin tuyển dụng - Chỉ Admin hoặc Recruiter sở hữu bài
    // Lưu ý: Recruiter KHÔNG được tự chuyển status thành 'open' (được chặn trong service)
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<JobPostResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String reason
    ) {
        return ResponseEntity.ok(jobPostService.updateStatus(id, status, reason));
    }

    // Tạo tin tuyển dụng - Chỉ Admin hoặc Nhà tuyển dụng đã đăng nhập
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<JobPostResponse> create(
            @Valid @RequestBody JobPostRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobPostService.create(request));
    }

    // Lấy danh sách việc làm nổi bật (Công khai)
    @GetMapping("/featured")
    public ResponseEntity<List<JobPostResponse>> getFeatured(
            @RequestParam(required = false) Long categoryId
    ) {
        return ResponseEntity.ok(jobPostService.getFeaturedJobs(categoryId));
    }

    // Lấy danh sách việc làm có phân trang và bộ lọc nâng cao (Công khai)
    @GetMapping
    public ResponseEntity<Page<JobPostResponse>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) BigDecimal salaryMin,
            @RequestParam(required = false) BigDecimal salaryMax,
            @RequestParam(required = false) String workingMode,
            @RequestParam(required = false) List<Long> skillIds,
            @RequestParam(required = false) Long specializationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "newest") String sort
    ) {
        return ResponseEntity.ok(
                jobPostService.getAll(
                        keyword,
                        status,
                        companyId,
                        categoryId,
                        provinceId,
                        salaryMin,
                        salaryMax,
                        workingMode,
                        skillIds,
                        specializationId,
                        page,
                        size,
                        sort
                )
        );
    }

    // Lấy chi tiết tin tuyển dụng (Công khai nếu open; nếu draft/closed chỉ Admin hoặc Recruiter của cty)
    @GetMapping("/{id}")
    public ResponseEntity<JobPostResponse> getById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(jobPostService.getById(id));
    }

    // Cập nhật tin tuyển dụng - Chỉ Admin hoặc Recruiter sở hữu bài
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<JobPostResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody JobPostRequest request
    ) {
        return ResponseEntity.ok(
                jobPostService.update(id, request)
        );
    }

    // Xóa tin tuyển dụng - Chỉ Admin hoặc Recruiter sở hữu bài
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {
        jobPostService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Lấy danh sách chuyên môn của tin tuyển dụng (Công khai)
    @GetMapping("/{id}/specializations")
    public ResponseEntity<List<com.kiemjob.backend.dto.job.JobSpecializationResponse>> getSpecializations(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(jobPostService.getSpecializationsByJobPostId(id));
    }

    // Cập nhật danh sách chuyên môn của tin tuyển dụng - Chỉ Admin hoặc Recruiter sở hữu bài
    @PutMapping("/{id}/specializations")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<List<com.kiemjob.backend.dto.job.JobSpecializationResponse>> updateSpecializations(
            @PathVariable Long id,
            @Valid @RequestBody com.kiemjob.backend.dto.cv.UpdateJobPostSpecializationsRequest request
    ) {
        return ResponseEntity.ok(
                jobPostService.updateJobPostSpecializations(id, request.getSpecializationIds())
        );
    }

    // Lấy danh sách địa chỉ làm việc của tin tuyển dụng (Công khai)
    @GetMapping("/{id}/addresses")
    public ResponseEntity<List<com.kiemjob.backend.dto.job.JobAddressResponse>> getAddresses(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(jobPostService.getAddressesByJobPostId(id));
    }

    // Cập nhật danh sách địa chỉ làm việc của tin tuyển dụng - Chỉ Admin hoặc Recruiter sở hữu bài
    @PutMapping("/{id}/addresses")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<List<com.kiemjob.backend.dto.job.JobAddressResponse>> updateAddresses(
            @PathVariable Long id,
            @RequestBody List<com.kiemjob.backend.dto.job.JobAddressRequest> requests
    ) {
        return ResponseEntity.ok(
                jobPostService.updateJobPostAddresses(id, requests)
        );
    }
}
