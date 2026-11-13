package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.*;
import com.kiemjob.backend.entity.CvCertificate;
import com.kiemjob.backend.repository.CvCertificateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CvCertificateService {

    private final CvCertificateRepository repository;
    private final CvOwnershipService ownershipService;

    // --- CandidateId-based methods (standard REST) ---

    @Transactional(readOnly = true)
    public List<CvCertificateResponse> getAll(Long candidateId, Long cvId) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        return repository.findAllByCvIdOrderByCertificateDateDesc(cvId)
                .stream().map(CvCertificateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CvCertificateResponse getById(Long candidateId, Long cvId, Long id) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);
        return repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .map(CvCertificateResponse::from)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Không tìm thấy chứng chỉ"));
    }

    public CvCertificateResponse create(
            Long candidateId, Long cvId, CvCertificateRequest request) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvCertificate entity = CvCertificate.builder()
                .cvId(cvId)
                .certificateDate(request.getCertificateDate())
                .certificateName(request.getCertificateName().trim())
                .description(normalize(request.getDescription()))
                .build();

        return CvCertificateResponse.from(repository.save(entity));
    }

    public CvCertificateResponse update(
            Long candidateId, Long cvId, Long id,
            CvCertificateRequest request) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvCertificate entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Không tìm thấy chứng chỉ"));

        entity.setCertificateDate(request.getCertificateDate());
        entity.setCertificateName(request.getCertificateName().trim());
        entity.setDescription(normalize(request.getDescription()));

        return CvCertificateResponse.from(repository.save(entity));
    }

    public void delete(Long candidateId, Long cvId, Long id) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        CvCertificate entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Không tìm thấy chứng chỉ"));

        repository.delete(entity);
    }

    // --- AccountId-based methods ---

    @Transactional(readOnly = true)
    public List<CvCertificateResponse> getAllByAccountId(Long cvId, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);
        return repository.findAllByCvIdOrderByCertificateDateDesc(cvId)
                .stream().map(CvCertificateResponse::from).toList();
    }

    public CvCertificateResponse createByAccountId(
            Long cvId, Long accountId, CvCertificateRequest request) {
        ownershipService.checkOwnership(cvId, accountId);

        CvCertificate entity = CvCertificate.builder()
                .cvId(cvId)
                .certificateDate(request.getCertificateDate())
                .certificateName(request.getCertificateName().trim())
                .description(normalize(request.getDescription()))
                .build();

        return CvCertificateResponse.from(repository.save(entity));
    }

    public CvCertificateResponse updateByAccountId(
            Long cvId, Long id, Long accountId,
            CvCertificateRequest request) {
        ownershipService.checkOwnership(cvId, accountId);

        CvCertificate entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new org.springframework.web.server
                        .ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Không tìm thấy chứng chỉ"));

        entity.setCertificateDate(request.getCertificateDate());
        entity.setCertificateName(request.getCertificateName().trim());
        entity.setDescription(normalize(request.getDescription()));

        return CvCertificateResponse.from(repository.save(entity));
    }

    public void deleteByAccountId(Long cvId, Long id, Long accountId) {
        ownershipService.checkOwnership(cvId, accountId);

        CvCertificate entity = repository.findById(id)
                .filter(item -> item.getCvId().equals(cvId))
                .orElseThrow(() -> new org.springframework.web.server
                        .ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Không tìm thấy chứng chỉ"));

        repository.delete(entity);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}