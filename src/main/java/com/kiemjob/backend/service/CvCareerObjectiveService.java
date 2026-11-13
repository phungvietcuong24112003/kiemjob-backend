package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvCareerObjectiveRequest;
import com.kiemjob.backend.dto.cv.CvCareerObjectiveResponse;
import com.kiemjob.backend.entity.CvCareerObjective;
import com.kiemjob.backend.repository.CvCareerObjectiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CvCareerObjectiveService {

    private final CvCareerObjectiveRepository repository;
    private final CvOwnershipService ownershipService;

    // --- Candidate-based methods (/api/candidates/{candidateId}/cvs/{cvId}/career-objective) ---

    @Transactional(readOnly = true)
    public CvCareerObjectiveResponse getByCandidate(Long candidateId, Long cvId) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        return repository.findByCvId(cvId)
            .map(CvCareerObjectiveResponse::from)
            .orElseGet(() -> new CvCareerObjectiveResponse(null, cvId, ""));
    }

    public CvCareerObjectiveResponse saveOrUpdateByCandidate(
            Long candidateId,
            Long cvId,
            CvCareerObjectiveRequest request) {

        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvCareerObjective entity = repository.findByCvId(cvId)
            .orElseGet(() -> CvCareerObjective.builder()
                .cvId(cvId)
                .build());

        String content = request != null && request.getContent() != null ? request.getContent().trim() : "";
        entity.setContent(content);

        return CvCareerObjectiveResponse.from(repository.save(entity));
    }

    public void deleteByCandidate(Long candidateId, Long cvId) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        repository.findByCvId(cvId).ifPresent(repository::delete);
    }

    // --- Account-based methods (/api/cvs/{cvId}/career-objective) ---

    @Transactional(readOnly = true)
    public CvCareerObjectiveResponse getByCvId(
            Long cvId,
            Long accountId) {

        ownershipService.checkOwnership(cvId, accountId);

        return repository.findByCvId(cvId)
            .map(CvCareerObjectiveResponse::from)
            .orElseGet(() -> new CvCareerObjectiveResponse(null, cvId, ""));
    }

    public CvCareerObjectiveResponse saveOrUpdate(
            Long cvId,
            Long accountId,
            CvCareerObjectiveRequest request) {

        ownershipService.checkOwnership(cvId, accountId);

        CvCareerObjective entity = repository.findByCvId(cvId)
            .orElseGet(() -> CvCareerObjective.builder()
                .cvId(cvId)
                .build());

        String content = request != null && request.getContent() != null ? request.getContent().trim() : "";
        entity.setContent(content);

        return CvCareerObjectiveResponse.from(repository.save(entity));
    }

    public void delete(Long cvId, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);
        repository.findByCvId(cvId).ifPresent(repository::delete);
    }
}