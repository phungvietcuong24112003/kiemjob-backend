package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.job.JobDetailRequest;
import com.kiemjob.backend.dto.job.JobDetailResponse;
import com.kiemjob.backend.entity.JobDetail;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.repository.JobDetailRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.security.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobDetailService {

    private final JobDetailRepository jobDetailRepository;
    private final JobPostRepository jobPostRepository;
    private final CurrentUserService currentUserService;

    // Thêm mục chi tiết vào tin tuyển dụng
    @Transactional
    public JobDetailResponse create(
            Long jobId,
            JobDetailRequest request
    ) {
        JobPost jobPost = findJobPost(jobId);
        currentUserService.assertCanManageJobPost(jobPost);

        JobDetail jobDetail = new JobDetail();
        jobDetail.setJobPost(jobPost);
        applyRequest(jobDetail, request);

        return JobDetailResponse.fromEntity(
                jobDetailRepository.save(jobDetail)
        );
    }

    // Lấy danh sách mục chi tiết của một tin
    @Transactional(readOnly = true)
    public List<JobDetailResponse> getAll(Long jobId) {
        ensureJobPostExists(jobId);

        return jobDetailRepository.findByJobPost_IdOrderByIdAsc(jobId)
                .stream()
                .map(JobDetailResponse::fromEntity)
                .toList();
    }

    // Lấy một mục chi tiết
    @Transactional(readOnly = true)
    public JobDetailResponse getById(Long jobId, Long id) {
        return JobDetailResponse.fromEntity(findJobDetail(jobId, id));
    }

    // Cập nhật mục chi tiết
    @Transactional
    public JobDetailResponse update(
            Long jobId,
            Long id,
            JobDetailRequest request
    ) {
        JobPost jobPost = findJobPost(jobId);
        currentUserService.assertCanManageJobPost(jobPost);

        JobDetail jobDetail = findJobDetail(jobId, id);

        applyRequest(jobDetail, request);

        return JobDetailResponse.fromEntity(
                jobDetailRepository.save(jobDetail)
        );
    }

    // Xóa mục chi tiết
    @Transactional
    public void delete(Long jobId, Long id) {
        JobPost jobPost = findJobPost(jobId);
        currentUserService.assertCanManageJobPost(jobPost);

        JobDetail jobDetail = findJobDetail(jobId, id);
        jobDetailRepository.delete(jobDetail);
    }

    private void applyRequest(
            JobDetail jobDetail,
            JobDetailRequest request
    ) {
        jobDetail.setTitle(request.getTitle().trim());
        jobDetail.setContent(normalizeContent(request.getContent()));
    }

    private JobPost findJobPost(Long jobId) {
        return jobPostRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy tin tuyển dụng có ID: " + jobId
                ));
    }

    private void ensureJobPostExists(Long jobId) {
        if (!jobPostRepository.existsById(jobId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy tin tuyển dụng có ID: " + jobId
            );
        }
    }

    private JobDetail findJobDetail(Long jobId, Long id) {
        return jobDetailRepository.findByIdAndJobPost_Id(id, jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy mục chi tiết"
                ));
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return content.trim();
    }
}
