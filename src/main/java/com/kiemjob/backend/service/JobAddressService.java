package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.job.JobAddressRequest;
import com.kiemjob.backend.dto.job.JobAddressResponse;
import com.kiemjob.backend.entity.JobAddress;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.Province;
import com.kiemjob.backend.repository.JobAddressRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.repository.ProvinceRepository;
import com.kiemjob.backend.security.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobAddressService {

    private final JobAddressRepository jobAddressRepository;
    private final JobPostRepository jobPostRepository;
    private final ProvinceRepository provinceRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public JobAddressResponse create(JobAddressRequest request) {
        if (request.getJobPostId() == null) {
            throw new IllegalArgumentException("ID tin tuyển dụng không được để trống");
        }

        JobPost jobPost = jobPostRepository.findById(request.getJobPostId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy tin tuyển dụng"
                        ));

        currentUserService.assertCanManageJobPost(jobPost);

        Province province = provinceRepository.findById(request.getProvinceId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy tỉnh/thành phố"
                        ));

        String detail = (request.getAddressDetail() != null && !request.getAddressDetail().isBlank())
                ? request.getAddressDetail().trim()
                : null;

        boolean duplicated = jobAddressRepository.existsAddress(
                request.getJobPostId(),
                request.getProvinceId(),
                detail
        );

        if (duplicated) {
            throw new IllegalArgumentException(
                    "Địa điểm này đã tồn tại trong tin tuyển dụng"
            );
        }

        JobAddress jobAddress = JobAddress.builder()
                .jobPost(jobPost)
                .province(province)
                .addressDetail(detail)
                .build();

        return toResponse(jobAddressRepository.save(jobAddress));
    }

    @Transactional(readOnly = true)
    public JobAddressResponse getById(Long id) {

        JobAddress jobAddress = jobAddressRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy địa điểm làm việc"
                        ));

        return toResponse(jobAddress);
    }

    @Transactional(readOnly = true)
    public List<JobAddressResponse> getByJobPostId(Long jobPostId) {

        if (!jobPostRepository.existsById(jobPostId)) {
            throw new IllegalArgumentException(
                    "Không tìm thấy tin tuyển dụng"
            );
        }

        return jobAddressRepository.findByJobPost_Id(jobPostId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public JobAddressResponse update(
            Long id,
            JobAddressRequest request
    ) {

        JobAddress jobAddress = jobAddressRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy địa điểm làm việc"
                        ));

        currentUserService.assertCanManageJobPost(jobAddress.getJobPost());

        Long targetJobPostId = request.getJobPostId() != null
                ? request.getJobPostId()
                : jobAddress.getJobPost().getId();

        JobPost jobPost = jobPostRepository.findById(targetJobPostId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy tin tuyển dụng"
                        ));

        if (!targetJobPostId.equals(jobAddress.getJobPost().getId())) {
            currentUserService.assertCanManageJobPost(jobPost);
        }

        Province province = provinceRepository.findById(request.getProvinceId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy tỉnh/thành phố"
                        ));

        String detail = (request.getAddressDetail() != null && !request.getAddressDetail().isBlank())
                ? request.getAddressDetail().trim()
                : null;

        boolean duplicated = jobAddressRepository.existsAddressExcept(
                targetJobPostId,
                request.getProvinceId(),
                detail,
                id
        );

        if (duplicated) {
            throw new IllegalArgumentException(
                    "Địa điểm này đã tồn tại trong tin tuyển dụng"
            );
        }

        jobAddress.setJobPost(jobPost);
        jobAddress.setProvince(province);
        jobAddress.setAddressDetail(detail);

        return toResponse(jobAddressRepository.save(jobAddress));
    }

    @Transactional
    public List<JobAddressResponse> syncAddresses(Long jobPostId, List<JobAddressRequest> requests) {
        JobPost jobPost = jobPostRepository.findById(jobPostId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Không tìm thấy tin tuyển dụng"));

        currentUserService.assertCanManageJobPost(jobPost);

        jobAddressRepository.deleteByJobPost_Id(jobPostId);
        jobAddressRepository.flush();

        if (requests != null && !requests.isEmpty()) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (JobAddressRequest req : requests) {
                if (req == null || req.getProvinceId() == null) continue;

                String detail = (req.getAddressDetail() != null && !req.getAddressDetail().isBlank())
                        ? req.getAddressDetail().trim()
                        : null;

                String dedupeKey = req.getProvinceId() + "_" + (detail == null ? "" : detail.toLowerCase());
                if (seen.contains(dedupeKey)) {
                    continue;
                }
                seen.add(dedupeKey);

                Province province = provinceRepository.findById(req.getProvinceId())
                        .orElseThrow(() ->
                                new IllegalArgumentException("Không tìm thấy tỉnh/thành phố có ID: " + req.getProvinceId()));

                JobAddress jobAddress = JobAddress.builder()
                        .jobPost(jobPost)
                        .province(province)
                        .addressDetail(detail)
                        .build();

                jobAddressRepository.save(jobAddress);
            }
        }

        return getByJobPostId(jobPostId);
    }

    @Transactional
    public void delete(Long id) {

        JobAddress jobAddress = jobAddressRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy địa điểm làm việc"
                        ));

        currentUserService.assertCanManageJobPost(jobAddress.getJobPost());

        jobAddressRepository.delete(jobAddress);
    }

    public JobAddressResponse toResponse(JobAddress jobAddress) {
        if (jobAddress == null) return null;

        return JobAddressResponse.builder()
                .id(jobAddress.getId())
                .jobPostId(jobAddress.getJobPost() != null ? jobAddress.getJobPost().getId() : null)
                .jobTitle(jobAddress.getJobPost() != null ? jobAddress.getJobPost().getTitle() : null)
                .provinceId(jobAddress.getProvince() != null ? jobAddress.getProvince().getId() : null)
                .provinceName(jobAddress.getProvince() != null ? jobAddress.getProvince().getName() : null)
                .addressDetail(jobAddress.getAddressDetail())
                .build();
    }
}
