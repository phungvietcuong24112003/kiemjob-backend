package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvSkillGroupRequest;
import com.kiemjob.backend.dto.cv.CvSkillGroupResponse;
import com.kiemjob.backend.entity.CvSkillGroup;
import com.kiemjob.backend.repository.CvSkillGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CvSkillGroupService {

    private final CvSkillGroupRepository repository;
    private final CvOwnershipService ownershipService;

    // --- CandidateId-based methods (standard REST /api/candidates/{candidateId}/cvs/{cvId}/skill-groups) ---

    @Transactional(readOnly = true)
    public List<CvSkillGroupResponse> getAll(Long candidateId, Long cvId) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        return repository.findAllByCvIdOrderByDisplayOrderAscIdAsc(cvId)
                .stream()
                .map(CvSkillGroupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CvSkillGroupResponse getById(Long candidateId, Long cvId, Long id) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        return repository.findByIdAndCvId(id, cvId)
                .map(CvSkillGroupResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhóm kỹ năng"
                ));
    }

    public CvSkillGroupResponse create(Long candidateId, Long cvId, CvSkillGroupRequest request) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        int order = request.getDisplayOrder() != null
                ? request.getDisplayOrder()
                : repository.countByCvId(cvId);

        CvSkillGroup entity = CvSkillGroup.builder()
                .cvId(cvId)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .displayOrder(order)
                .build();

        return CvSkillGroupResponse.from(repository.save(entity));
    }

    public CvSkillGroupResponse update(Long candidateId, Long cvId, Long id, CvSkillGroupRequest request) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvSkillGroup entity = repository.findByIdAndCvId(id, cvId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhóm kỹ năng"
                ));

        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent().trim());
        if (request.getDisplayOrder() != null) {
            entity.setDisplayOrder(request.getDisplayOrder());
        }

        return CvSkillGroupResponse.from(repository.save(entity));
    }

    public void delete(Long candidateId, Long cvId, Long id) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvSkillGroup entity = repository.findByIdAndCvId(id, cvId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhóm kỹ năng"
                ));

        repository.delete(entity);
    }

    // --- AccountId-based methods (/api/cvs/{cvId}/skill-groups) ---

    @Transactional(readOnly = true)
    public List<CvSkillGroupResponse> getAllByAccountId(Long cvId, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);
        return repository.findAllByCvIdOrderByDisplayOrderAscIdAsc(cvId)
                .stream()
                .map(CvSkillGroupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CvSkillGroupResponse getByIdByAccountId(Long cvId, Long id, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);
        return repository.findByIdAndCvId(id, cvId)
                .map(CvSkillGroupResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhóm kỹ năng"
                ));
    }

    public CvSkillGroupResponse createByAccountId(Long cvId, Long accountId, CvSkillGroupRequest request) {
        ownershipService.checkOwnership(cvId, accountId);

        int order = request.getDisplayOrder() != null
                ? request.getDisplayOrder()
                : repository.countByCvId(cvId);

        CvSkillGroup entity = CvSkillGroup.builder()
                .cvId(cvId)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .displayOrder(order)
                .build();

        return CvSkillGroupResponse.from(repository.save(entity));
    }

    public CvSkillGroupResponse updateByAccountId(Long cvId, Long id, Long accountId, CvSkillGroupRequest request) {
        ownershipService.checkOwnership(cvId, accountId);

        CvSkillGroup entity = repository.findByIdAndCvId(id, cvId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhóm kỹ năng"
                ));

        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent().trim());
        if (request.getDisplayOrder() != null) {
            entity.setDisplayOrder(request.getDisplayOrder());
        }

        return CvSkillGroupResponse.from(repository.save(entity));
    }

    public void deleteByAccountId(Long cvId, Long id, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);

        CvSkillGroup entity = repository.findByIdAndCvId(id, cvId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhóm kỹ năng"
                ));

        repository.delete(entity);
    }
}