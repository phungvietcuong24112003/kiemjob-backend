package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvProjectRequest;
import com.kiemjob.backend.dto.cv.CvProjectResponse;
import com.kiemjob.backend.entity.CvProject;
import com.kiemjob.backend.repository.CvProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CvProjectService {

    private final CvProjectRepository repository;
    private final CvOwnershipService ownershipService;

    // --- CandidateId-based methods (standard REST) ---

    @Transactional(readOnly = true)
    public List<CvProjectResponse> getAll(Long candidateId, Long cvId) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        return repository.findAllByCvIdOrderByStartDateDesc(cvId)
                .stream().map(CvProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CvProjectResponse getById(Long candidateId, Long cvId, Long id) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        return repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .map(CvProjectResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy dự án"));
    }

    public CvProjectResponse create(
            Long candidateId, Long cvId, CvProjectRequest request) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        validateDates(request);

        boolean isCurrent = Boolean.TRUE.equals(request.getIsCurrent());

        CvProject entity = CvProject.builder()
                .cvId(cvId)
                .projectName(request.getProjectName().trim())
                .role(normalize(request.getRole()))
                .startDate(request.getStartDate())
                .endDate(isCurrent ? null : request.getEndDate())
                .isCurrent(isCurrent)
                .description(normalize(request.getDescription()))
                .technologies(normalize(request.getTechnologies()))
                .projectUrl(normalize(request.getProjectUrl()))
                .repositoryUrl(normalize(request.getRepositoryUrl()))
                .build();

        return CvProjectResponse.from(repository.save(entity));
    }

    public CvProjectResponse update(
            Long candidateId, Long cvId, Long id,
            CvProjectRequest request) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        validateDates(request);

        CvProject entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy dự án"));

        boolean isCurrent = Boolean.TRUE.equals(request.getIsCurrent());

        entity.setProjectName(request.getProjectName().trim());
        entity.setRole(normalize(request.getRole()));
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(isCurrent ? null : request.getEndDate());
        entity.setIsCurrent(isCurrent);
        entity.setDescription(normalize(request.getDescription()));
        entity.setTechnologies(normalize(request.getTechnologies()));
        entity.setProjectUrl(normalize(request.getProjectUrl()));
        entity.setRepositoryUrl(normalize(request.getRepositoryUrl()));

        return CvProjectResponse.from(repository.save(entity));
    }

    public void delete(Long candidateId, Long cvId, Long id) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvProject entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy dự án"));

        repository.delete(entity);
    }

    // --- AccountId-based methods ---

    @Transactional(readOnly = true)
    public List<CvProjectResponse> getAllByAccountId(Long cvId, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);
        return repository.findAllByCvIdOrderByStartDateDesc(cvId)
                .stream().map(CvProjectResponse::from).toList();
    }

    public CvProjectResponse createByAccountId(
            Long cvId, Long accountId, CvProjectRequest request) {
        ownershipService.checkOwnership(cvId, accountId);
        validateDates(request);

        boolean isCurrent = Boolean.TRUE.equals(request.getIsCurrent());

        CvProject entity = CvProject.builder()
                .cvId(cvId)
                .projectName(request.getProjectName().trim())
                .role(normalize(request.getRole()))
                .startDate(request.getStartDate())
                .endDate(isCurrent ? null : request.getEndDate())
                .isCurrent(isCurrent)
                .description(normalize(request.getDescription()))
                .technologies(normalize(request.getTechnologies()))
                .projectUrl(normalize(request.getProjectUrl()))
                .repositoryUrl(normalize(request.getRepositoryUrl()))
                .build();

        return CvProjectResponse.from(repository.save(entity));
    }

    public CvProjectResponse updateByAccountId(
            Long cvId, Long id, Long accountId,
            CvProjectRequest request) {
        ownershipService.checkOwnership(cvId, accountId);
        validateDates(request);

        CvProject entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy dự án"));

        boolean isCurrent = Boolean.TRUE.equals(request.getIsCurrent());

        entity.setProjectName(request.getProjectName().trim());
        entity.setRole(normalize(request.getRole()));
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(isCurrent ? null : request.getEndDate());
        entity.setIsCurrent(isCurrent);
        entity.setDescription(normalize(request.getDescription()));
        entity.setTechnologies(normalize(request.getTechnologies()));
        entity.setProjectUrl(normalize(request.getProjectUrl()));
        entity.setRepositoryUrl(normalize(request.getRepositoryUrl()));

        return CvProjectResponse.from(repository.save(entity));
    }

    public void deleteByAccountId(Long cvId, Long id, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);

        CvProject entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy dự án"));

        repository.delete(entity);
    }

    private void validateDates(CvProjectRequest request) {
        if (request.getStartDate() != null
                && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu"
            );
        }
        if (Boolean.TRUE.equals(request.getIsCurrent())
                && request.getEndDate() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dự án đang thực hiện thì ngày kết thúc phải để trống"
            );
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
