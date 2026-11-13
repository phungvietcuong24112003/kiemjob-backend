
package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.savedjob.SavedJobRequest;
import com.kiemjob.backend.dto.savedjob.SavedJobResponse;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.SavedJob;
import com.kiemjob.backend.repository.CandidateRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.repository.SavedJobRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SavedJobService {

    private final SavedJobRepository savedJobRepository;
    private final CandidateRepository candidateRepository;
    private final JobPostRepository jobPostRepository;

    public SavedJobResponse saveJob(
            Long accountId,
            SavedJobRequest request) {

        Candidate candidate = findCandidateByAccountId(accountId);

        JobPost jobPost = jobPostRepository.findById(request.getJobId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy tin tuyển dụng"));

        if (savedJobRepository.existsByCandidate_IdAndJobPost_Id(
                candidate.getId(), jobPost.getId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Tin tuyển dụng này đã được lưu");
        }

        SavedJob savedJob = new SavedJob();
        savedJob.setCandidate(candidate);
        savedJob.setJobPost(jobPost);

        try {
            return SavedJobResponse.fromEntity(
                savedJobRepository.saveAndFlush(savedJob));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Tin tuyển dụng này đã được lưu");
        }
    }

    @Transactional(readOnly = true)
    public List<SavedJobResponse> getSavedJobs(Long accountId) {
        Candidate candidate = findCandidateByAccountId(accountId);

        return savedJobRepository
            .findByCandidate_IdOrderByIdDesc(candidate.getId())
            .stream()
            .map(SavedJobResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public boolean isSaved(Long accountId, Long jobId) {
        Candidate candidate = findCandidateByAccountId(accountId);

        if (!jobPostRepository.existsById(jobId)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy tin tuyển dụng");
        }

        return savedJobRepository
            .existsByCandidate_IdAndJobPost_Id(candidate.getId(), jobId);
    }

    public void removeSavedJob(Long accountId, Long jobId) {
        Candidate candidate = findCandidateByAccountId(accountId);

        SavedJob savedJob = savedJobRepository
            .findByCandidate_IdAndJobPost_Id(candidate.getId(), jobId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy tin đã lưu"));

        savedJobRepository.delete(savedJob);
    }

    private Candidate findCandidateByAccountId(Long accountId) {
        return candidateRepository.findByAccountId(accountId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy hồ sơ ứng viên của tài khoản này"
            ));
    }
}
