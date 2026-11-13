package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.job.JobSkillRequest;
import com.kiemjob.backend.dto.job.JobSkillResponse;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.JobSkill;
import com.kiemjob.backend.entity.Skill;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.repository.JobSkillRepository;
import com.kiemjob.backend.repository.SkillRepository;
import com.kiemjob.backend.security.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobSkillService {

    private final JobSkillRepository jobSkillRepository;
    private final JobPostRepository jobPostRepository;
    private final SkillRepository skillRepository;
    private final CurrentUserService currentUserService;

    // Thêm kỹ năng vào tin tuyển dụng
    @Transactional
    public JobSkillResponse create(Long jobId, JobSkillRequest request) {
        JobPost jobPost = findJobPost(jobId);
        currentUserService.assertCanManageJobPost(jobPost);

        Skill skill = findSkill(request.getSkillId());

        if (jobSkillRepository.existsByJobPost_IdAndSkill_Id(
                jobId, request.getSkillId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tin tuyển dụng đã có kỹ năng này"
            );
        }

        JobSkill jobSkill = new JobSkill();
        jobSkill.setJobPost(jobPost);
        jobSkill.setSkill(skill);
        jobSkill.setDescription(normalizeDescription(request.getDescription()));

        try {
            return JobSkillResponse.fromEntity(
                    jobSkillRepository.saveAndFlush(jobSkill)
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Kỹ năng đã tồn tại trong tin tuyển dụng hoặc dữ liệu không hợp lệ"
            );
        }
    }

    // Lấy danh sách kỹ năng của tin tuyển dụng (Công khai)
    @Transactional(readOnly = true)
    public List<JobSkillResponse> getAll(Long jobId) {
        ensureJobPostExists(jobId);

        return jobSkillRepository.findByJobPost_IdOrderByIdAsc(jobId)
                .stream()
                .map(JobSkillResponse::fromEntity)
                .toList();
    }

    // Lấy chi tiết một kỹ năng được gắn vào tin (Công khai)
    @Transactional(readOnly = true)
    public JobSkillResponse getById(Long jobId, Long id) {
        return JobSkillResponse.fromEntity(findJobSkill(jobId, id));
    }

    // Cập nhật kỹ năng hoặc mô tả
    @Transactional
    public JobSkillResponse update(
            Long jobId,
            Long id,
            JobSkillRequest request
    ) {
        JobPost jobPost = findJobPost(jobId);
        currentUserService.assertCanManageJobPost(jobPost);

        JobSkill jobSkill = findJobSkill(jobId, id);
        Skill skill = findSkill(request.getSkillId());

        if (jobSkillRepository.existsByJobPost_IdAndSkill_IdAndIdNot(
                jobId, request.getSkillId(), id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tin tuyển dụng đã có kỹ năng này"
            );
        }

        jobSkill.setSkill(skill);
        jobSkill.setDescription(normalizeDescription(request.getDescription()));

        try {
            return JobSkillResponse.fromEntity(
                    jobSkillRepository.saveAndFlush(jobSkill)
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Kỹ năng đã tồn tại trong tin tuyển dụng hoặc dữ liệu không hợp lệ"
            );
        }
    }

    // Xóa kỹ năng khỏi tin tuyển dụng
    @Transactional
    public void delete(Long jobId, Long id) {
        JobPost jobPost = findJobPost(jobId);
        currentUserService.assertCanManageJobPost(jobPost);

        JobSkill jobSkill = findJobSkill(jobId, id);
        jobSkillRepository.delete(jobSkill);
        jobSkillRepository.flush();
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

    private Skill findSkill(Long skillId) {
        return skillRepository.findById(skillId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy kỹ năng có ID: " + skillId
                ));
    }

    private JobSkill findJobSkill(Long jobId, Long id) {
        return jobSkillRepository.findByIdAndJobPost_Id(id, jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy kỹ năng được gắn với tin tuyển dụng này"
                ));
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        return description.trim();
    }
}
