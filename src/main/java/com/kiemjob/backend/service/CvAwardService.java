package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvAwardRequest;
import com.kiemjob.backend.dto.cv.CvAwardResponse;
import com.kiemjob.backend.entity.CvAward;
import com.kiemjob.backend.repository.CvAwardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CvAwardService {

    private final CvAwardRepository repository;
    private final CvOwnershipService ownershipService;

    // --- CandidateId-based methods (standard REST) ---

    @Transactional(readOnly = true)
    public List<CvAwardResponse> getAll(Long candidateId, Long cvId) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        return repository.findAllByCvIdOrderByAwardDateDesc(cvId)
                .stream().map(CvAwardResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CvAwardResponse getById(Long candidateId, Long cvId, Long id) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        return repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .map(CvAwardResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy giải thưởng"));
    }

    public CvAwardResponse create(
            Long candidateId, Long cvId, CvAwardRequest request) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvAward entity = CvAward.builder()
                .cvId(cvId)
                .awardDate(request.getAwardDate())
                .awardName(request.getAwardName().trim())
                .description(normalize(request.getDescription()))
                .build();

        return CvAwardResponse.from(repository.save(entity));
    }

    public CvAwardResponse update(
            Long candidateId, Long cvId, Long id,
            CvAwardRequest request) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvAward entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy giải thưởng"));

        entity.setAwardDate(request.getAwardDate());
        entity.setAwardName(request.getAwardName().trim());
        entity.setDescription(normalize(request.getDescription()));

        return CvAwardResponse.from(repository.save(entity));
    }

    public void delete(Long candidateId, Long cvId, Long id) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvAward entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy giải thưởng"));

        repository.delete(entity);
    }

    // --- AccountId-based methods ---

    @Transactional(readOnly = true)
    public List<CvAwardResponse> getAllByAccountId(Long cvId, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);
        return repository.findAllByCvIdOrderByAwardDateDesc(cvId)
                .stream().map(CvAwardResponse::from).toList();
    }

    public CvAwardResponse createByAccountId(
            Long cvId, Long accountId, CvAwardRequest request) {
        ownershipService.checkOwnership(cvId, accountId);

        CvAward entity = CvAward.builder()
                .cvId(cvId)
                .awardDate(request.getAwardDate())
                .awardName(request.getAwardName().trim())
                .description(normalize(request.getDescription()))
                .build();

        return CvAwardResponse.from(repository.save(entity));
    }

    public CvAwardResponse updateByAccountId(
            Long cvId, Long id, Long accountId,
            CvAwardRequest request) {
        ownershipService.checkOwnership(cvId, accountId);

        CvAward entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy giải thưởng"));

        entity.setAwardDate(request.getAwardDate());
        entity.setAwardName(request.getAwardName().trim());
        entity.setDescription(normalize(request.getDescription()));

        return CvAwardResponse.from(repository.save(entity));
    }

    public void deleteByAccountId(Long cvId, Long id, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);

        CvAward entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy giải thưởng"));

        repository.delete(entity);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
