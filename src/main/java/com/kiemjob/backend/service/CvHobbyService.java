package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvHobbyRequest;
import com.kiemjob.backend.dto.cv.CvHobbyResponse;
import com.kiemjob.backend.entity.CvHobby;
import com.kiemjob.backend.repository.CvHobbyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CvHobbyService {

    private final CvHobbyRepository repository;
    private final CvOwnershipService ownershipService;

    // --- CandidateId-based methods (standard REST) ---

    @Transactional(readOnly = true)
    public List<CvHobbyResponse> getAll(Long candidateId, Long cvId) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        return repository.findAllByCvIdOrderByIdAsc(cvId)
                .stream().map(CvHobbyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CvHobbyResponse getById(Long candidateId, Long cvId, Long id) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        return repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .map(CvHobbyResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sở thích"));
    }

    public CvHobbyResponse create(
            Long candidateId, Long cvId, CvHobbyRequest request) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        String trimmedName = request.getHobbyName().trim();
        if (repository.existsByCvIdAndHobbyNameIgnoreCase(cvId, trimmedName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sở thích này đã tồn tại trong CV"
            );
        }

        CvHobby entity = CvHobby.builder()
                .cvId(cvId)
                .hobbyName(trimmedName)
                .build();

        return CvHobbyResponse.from(repository.save(entity));
    }

    public CvHobbyResponse update(
            Long candidateId, Long cvId, Long id,
            CvHobbyRequest request) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvHobby entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sở thích"));

        String trimmedName = request.getHobbyName().trim();
        if (!entity.getHobbyName().equalsIgnoreCase(trimmedName)
                && repository.existsByCvIdAndHobbyNameIgnoreCase(cvId, trimmedName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sở thích này đã tồn tại trong CV"
            );
        }

        entity.setHobbyName(trimmedName);
        return CvHobbyResponse.from(repository.save(entity));
    }

    public void delete(Long candidateId, Long cvId, Long id) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvHobby entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sở thích"));

        repository.delete(entity);
    }

    // --- AccountId-based methods ---

    @Transactional(readOnly = true)
    public List<CvHobbyResponse> getAllByAccountId(Long cvId, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);
        return repository.findAllByCvIdOrderByIdAsc(cvId)
                .stream().map(CvHobbyResponse::from).toList();
    }

    public CvHobbyResponse createByAccountId(
            Long cvId, Long accountId, CvHobbyRequest request) {
        ownershipService.checkOwnership(cvId, accountId);

        String trimmedName = request.getHobbyName().trim();
        if (repository.existsByCvIdAndHobbyNameIgnoreCase(cvId, trimmedName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sở thích này đã tồn tại trong CV"
            );
        }

        CvHobby entity = CvHobby.builder()
                .cvId(cvId)
                .hobbyName(trimmedName)
                .build();

        return CvHobbyResponse.from(repository.save(entity));
    }

    public CvHobbyResponse updateByAccountId(
            Long cvId, Long id, Long accountId,
            CvHobbyRequest request) {
        ownershipService.checkOwnership(cvId, accountId);

        CvHobby entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sở thích"));

        String trimmedName = request.getHobbyName().trim();
        if (!entity.getHobbyName().equalsIgnoreCase(trimmedName)
                && repository.existsByCvIdAndHobbyNameIgnoreCase(cvId, trimmedName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sở thích này đã tồn tại trong CV"
            );
        }

        entity.setHobbyName(trimmedName);
        return CvHobbyResponse.from(repository.save(entity));
    }

    public void deleteByAccountId(Long cvId, Long id, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);

        CvHobby entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sở thích"));

        repository.delete(entity);
    }
}
