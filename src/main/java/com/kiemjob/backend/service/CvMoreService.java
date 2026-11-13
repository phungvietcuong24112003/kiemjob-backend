package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvMoreRequest;
import com.kiemjob.backend.dto.cv.CvMoreResponse;
import com.kiemjob.backend.entity.Cv;
import com.kiemjob.backend.entity.CvMore;
import com.kiemjob.backend.repository.CvMoreRepository;
import com.kiemjob.backend.repository.CvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CvMoreService {

    private final CvMoreRepository cvMoreRepository;
    private final CvRepository cvRepository;

    @Transactional
    public CvMoreResponse create(
            Long candidateId,
            Long cvId,
            CvMoreRequest request) {

        Cv cv = getOwnedCv(candidateId, cvId);

        CvMore cvMore = CvMore.builder()
                .cv(cv)
                .title(request.getTitle().trim())
                .content(normalize(request.getContent()))
                .build();

        return CvMoreResponse.fromEntity(
                cvMoreRepository.save(cvMore)
        );
    }

    @Transactional(readOnly = true)
    public List<CvMoreResponse> getAll(
            Long candidateId,
            Long cvId) {

        getOwnedCv(candidateId, cvId);

        return cvMoreRepository.findByCv_IdOrderByIdAsc(cvId)
                .stream()
                .map(CvMoreResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CvMoreResponse getById(
            Long candidateId,
            Long cvId,
            Long moreId) {

        getOwnedCv(candidateId, cvId);

        CvMore cvMore = getCvMore(moreId, cvId);

        return CvMoreResponse.fromEntity(cvMore);
    }

    @Transactional
    public CvMoreResponse update(
            Long candidateId,
            Long cvId,
            Long moreId,
            CvMoreRequest request) {

        getOwnedCv(candidateId, cvId);

        CvMore cvMore = getCvMore(moreId, cvId);

        cvMore.setTitle(request.getTitle().trim());
        cvMore.setContent(normalize(request.getContent()));

        return CvMoreResponse.fromEntity(
                cvMoreRepository.save(cvMore)
        );
    }

    @Transactional
    public void delete(
            Long candidateId,
            Long cvId,
            Long moreId) {

        getOwnedCv(candidateId, cvId);

        CvMore cvMore = getCvMore(moreId, cvId);

        cvMoreRepository.delete(cvMore);
    }

    private Cv getOwnedCv(Long candidateId, Long cvId) {
        return cvRepository.findByIdAndCandidate_Id(cvId, candidateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy CV thuộc ứng viên này"
                ));
    }

    private CvMore getCvMore(Long moreId, Long cvId) {
        return cvMoreRepository.findByIdAndCv_Id(moreId, cvId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin bổ sung trong CV này"
                ));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}